package com.spydrone.orthanc_scan_consumer.messaging;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.support.RabbitExceptionTranslator;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * Runs work on a channel of the shared connection that's closed for real afterwards instead of going back
 * to the channel cache. Use it for work that changes a channel's mode (transactions, publisher confirms)
 * or leaves messages unacked on failure: closing rolls back anything uncommitted and returns unacked
 * messages to their queue, and no other code is handed a channel left in that state.
 */
public final class DedicatedChannel {

	@FunctionalInterface
	public interface Work<T> {
		T run(Channel channel) throws IOException, TimeoutException, InterruptedException;
	}

	private DedicatedChannel() {
	}

	/**
	 * @param transactional true for a channel the connection factory has already put in transaction
	 *        mode (tx.select); commit with {@link Channel#txCommit()}
	 */
	public static <T> T run(ConnectionFactory connectionFactory, boolean transactional, Work<T> work) {
		Channel channel = connectionFactory.createConnection().createChannel(transactional);
		try {
			return work.run(channel);
		}
		// ShutdownSignalException: the broker closed the channel, e.g. publishing to an exchange that doesn't exist.
		catch (IOException | TimeoutException | ShutdownSignalException e) {
			throw RabbitExceptionTranslator.convertRabbitAccessException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw RabbitExceptionTranslator.convertRabbitAccessException(e);
		}
		finally {
			RabbitUtils.setPhysicalCloseRequired(channel, true);
			RabbitUtils.closeChannel(channel);
			RabbitUtils.clearPhysicalCloseRequired();
		}
	}
}
