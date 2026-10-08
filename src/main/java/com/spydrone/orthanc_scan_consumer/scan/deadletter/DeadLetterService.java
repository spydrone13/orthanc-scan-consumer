package com.spydrone.orthanc_scan_consumer.scan.deadletter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.rabbit.support.RabbitExceptionTranslator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lists, replays and discards the scans in the dead-letter queue.
 * <p>
 * AMQP can't browse a queue, so each call takes messages off it with basic.get and, before committing,
 * acks the ones it's done with and requeues the rest. Each call runs on its own channel in an AMQP
 * transaction: a replay's publish and ack commit together, requeued messages aren't handed back
 * until the commit (so one call never sees a message twice), and if anything fails the channel is
 * closed, which rolls the transaction back and returns every message it took.
 */
@Service
public class DeadLetterService {

	private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

	/** Messages looked at per call; anything further back in the queue is left for a later call. */
	static final int MAX_MESSAGES = 500;

	/** Left off replayed scans, so a replay that fails again is reported afresh. */
	private static final Set<String> FAILURE_HEADERS = Set.of(
			RepublishMessageRecoverer.X_EXCEPTION_MESSAGE, RepublishMessageRecoverer.X_EXCEPTION_STACKTRACE,
			RepublishMessageRecoverer.X_ORIGINAL_EXCHANGE, RepublishMessageRecoverer.X_ORIGINAL_ROUTING_KEY,
			FailedScanRecoverer.FAILED_AT_HEADER,
			"x-death", "x-first-death-exchange", "x-first-death-queue", "x-first-death-reason",
			"x-last-death-exchange", "x-last-death-queue", "x-last-death-reason");

	private final ConnectionFactory connectionFactory;
	private final JsonMapper json;
	private final String deadLetterQueue;
	private final String scansQueue;

	public DeadLetterService(ConnectionFactory connectionFactory, JsonMapper json,
			@Value("${app.scans.dead-letter-queue}") String deadLetterQueue,
			@Value("${app.scans.queue}") String scansQueue) {
		this.connectionFactory = connectionFactory;
		this.json = json;
		this.deadLetterQueue = deadLetterQueue;
		this.scansQueue = scansQueue;
	}

	/**
	 * @param total messages in the queue, which can be more than {@link #MAX_MESSAGES}
	 * @param deadLetters the oldest messages, up to {@link #MAX_MESSAGES}
	 */
	public record DeadLetters(long total, List<DeadLetter> deadLetters) {
	}

	public DeadLetters list() {
		return inTransaction(channel -> {
			long total = channel.messageCount(deadLetterQueue);
			List<DeadLetter> deadLetters = new ArrayList<>();
			forEachMessage(channel, (response, deadLetter) -> {
				deadLetters.add(deadLetter);
				channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
			});
			return new DeadLetters(total, deadLetters);
		});
	}

	/** Sends the matching scans back to the scans queue. Returns how many were replayed. */
	public int retry(Predicate<DeadLetter> which) {
		int replayed = take(which, (channel, response, deadLetter) -> channel.basicPublish("", scansQueue,
				withoutFailureHeaders(response.getProps()), response.getBody()));
		if (replayed > 0) {
			log.info("Replayed {} dead-lettered scan(s) to {}", replayed, scansQueue);
		}
		return replayed;
	}

	/** Removes the matching scans for good. Returns how many were removed. */
	public int discard(Predicate<DeadLetter> which) {
		return take(which, (channel, response, deadLetter) ->
				log.warn("Discarding dead-lettered scan {}: {}", deadLetter.id(), deadLetter.body()));
	}

	/** Runs {@code action} on each matching message and acks it; requeues the rest. */
	private int take(Predicate<DeadLetter> which, MessageAction action) {
		return inTransaction(channel -> {
			int[] taken = {0};
			forEachMessage(channel, (response, deadLetter) -> {
				long tag = response.getEnvelope().getDeliveryTag();
				if (which.test(deadLetter)) {
					action.apply(channel, response, deadLetter);
					channel.basicAck(tag, false);
					taken[0]++;
				}
				else {
					channel.basicNack(tag, false, true);
				}
			});
			return taken[0];
		});
	}

	private void forEachMessage(Channel channel, MessageHandler handler) throws IOException {
		for (int i = 0; i < MAX_MESSAGES; i++) {
			GetResponse response = channel.basicGet(deadLetterQueue, false);
			if (response == null) {
				return;
			}
			handler.handle(response, DeadLetter.of(response.getProps(), response.getBody(), json));
		}
	}

	private static AMQP.BasicProperties withoutFailureHeaders(AMQP.BasicProperties properties) {
		if (properties.getHeaders() == null) {
			return properties;
		}
		Map<String, Object> headers = new HashMap<>(properties.getHeaders());
		headers.keySet().removeAll(FAILURE_HEADERS);
		return properties.builder().headers(headers).build();
	}

	private <T> T inTransaction(ChannelWork<T> work) {
		// Transactional: the connection factory has already sent tx.select on it.
		Channel channel = connectionFactory.createConnection().createChannel(true);
		try {
			T result = work.run(channel);
			channel.txCommit();
			return result;
		}
		catch (IOException e) {
			throw RabbitExceptionTranslator.convertRabbitAccessException(e);
		}
		finally {
			// A physical close, not a return to the channel cache: anything not committed is rolled back
			// and every message still unacked goes back to the queue.
			RabbitUtils.setPhysicalCloseRequired(channel, true);
			RabbitUtils.closeChannel(channel);
			RabbitUtils.clearPhysicalCloseRequired();
		}
	}

	@FunctionalInterface
	private interface ChannelWork<T> {
		T run(Channel channel) throws IOException;
	}

	@FunctionalInterface
	private interface MessageHandler {
		void handle(GetResponse response, DeadLetter deadLetter) throws IOException;
	}

	@FunctionalInterface
	private interface MessageAction {
		void apply(Channel channel, GetResponse response, DeadLetter deadLetter) throws IOException;
	}
}
