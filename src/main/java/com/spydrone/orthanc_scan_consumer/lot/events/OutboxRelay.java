package com.spydrone.orthanc_scan_consumer.lot.events;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.AMQP;
import com.spydrone.orthanc_scan_consumer.messaging.DedicatedChannel;

import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes committed lot events from the outbox to the lots exchange, oldest first, and marks them
 * published once the broker has confirmed them. If the broker is unreachable or doesn't confirm, the
 * events stay unpublished and the next run tries again, so nothing is lost; a crash after the confirm
 * but before marking re-sends them, which is why subscribers de-duplicate on eventId.
 * <p>
 * One relay thread keeps events in order. Running more than one consumer instance would need the
 * batch rows locked (e.g. SELECT ... FOR UPDATE SKIP LOCKED) so instances don't publish the same rows.
 */
@Component
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private final OutboxEventRepository outbox;
	private final ConnectionFactory connectionFactory;
	private final JsonMapper json;
	private final String exchange;
	private final int batchSize;
	private final Duration confirmTimeout;
	private final Duration retention;
	/** So a broker outage is logged once, not on every run. */
	private boolean failing;

	public OutboxRelay(OutboxEventRepository outbox, ConnectionFactory connectionFactory, JsonMapper json,
			@Value("${app.lots.exchange}") String exchange,
			@Value("${app.lots.outbox.batch-size}") int batchSize,
			@Value("${app.lots.outbox.confirm-timeout}") Duration confirmTimeout,
			@Value("${app.lots.outbox.retention}") Duration retention) {
		this.outbox = outbox;
		this.connectionFactory = connectionFactory;
		this.json = json;
		this.exchange = exchange;
		this.batchSize = batchSize;
		this.confirmTimeout = confirmTimeout;
		this.retention = retention;
	}

	/** Publishes up to one batch. Returns how many were published. */
	@Scheduled(fixedDelayString = "${app.lots.outbox.relay-interval}")
	public int publishPending() {
		List<OutboxEvent> batch = outbox.findByPublishedAtIsNullOrderByIdAsc(Limit.of(batchSize));
		if (batch.isEmpty()) {
			return 0;
		}
		try {
			DedicatedChannel.run(connectionFactory, false, channel -> {
				channel.confirmSelect();
				for (OutboxEvent event : batch) {
					channel.basicPublish(exchange, event.getRoutingKey(), properties(event), body(event));
				}
				channel.waitForConfirmsOrDie(confirmTimeout.toMillis());
				return null;
			});
		}
		catch (AmqpException e) {
			if (!failing) {
				log.warn("Could not publish lot events to {}; they'll be sent once it's reachable: {}", exchange,
						e.getMessage());
				failing = true;
			}
			return 0;
		}
		if (failing) {
			log.info("Publishing lot events to {} again", exchange);
			failing = false;
		}
		Instant now = Instant.now();
		batch.forEach(event -> event.markPublished(now));
		outbox.saveAll(batch);
		return batch.size();
	}

	/** Published events are kept for a while to help trace what a subscriber was sent, then deleted. */
	@Scheduled(cron = "${app.lots.outbox.cleanup-cron}")
	public void deleteOldPublished() {
		int deleted = outbox.deletePublishedBefore(Instant.now().minus(retention));
		if (deleted > 0) {
			log.info("Deleted {} lot events published more than {} ago", deleted, retention);
		}
	}

	private byte[] body(OutboxEvent event) {
		LotEventMessage message = json.readValue(event.getPayload(), LotEventMessage.class).withSequence(event.getId());
		return json.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
	}

	private static AMQP.BasicProperties properties(OutboxEvent event) {
		return new AMQP.BasicProperties.Builder()
				.messageId(event.getEventId())
				.type(event.getRoutingKey())
				.contentType("application/json")
				.contentEncoding("UTF-8")
				.deliveryMode(2)
				.timestamp(Date.from(event.getCreatedAt()))
				.build();
	}
}
