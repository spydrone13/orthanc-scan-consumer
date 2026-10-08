package com.spydrone.orthanc_scan_consumer.scan;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.spydrone.orthanc_scan_consumer.scan.deadletter.FailedScanRecoverer;

/**
 * Mirrors the producer's declarations so either app can start first. Scans that still fail after the
 * listener's retries (see application.properties) are republished to the dead-letter queue with the
 * failure reason; anything rejected outside the retries is dead-lettered there by the queue's arguments.
 * Either way they wait to be retried or discarded at /dead-letters.
 */
@Configuration
public class RabbitConfig {

	@Bean
	DirectExchange scansExchange(@Value("${app.scans.exchange}") String name) {
		return new DirectExchange(name);
	}

	/**
	 * RabbitMQ won't change an existing queue's arguments: a queue declared before the dead-letter
	 * arguments existed must be deleted so it can be redeclared (see README).
	 */
	@Bean
	Queue scansQueue(@Value("${app.scans.queue}") String name,
			@Value("${app.scans.dead-letter-exchange}") String deadLetterExchange,
			@Value("${app.scans.dead-letter-queue}") String deadLetterQueue) {
		return QueueBuilder.durable(name)
				.deadLetterExchange(deadLetterExchange)
				.deadLetterRoutingKey(deadLetterQueue)
				.build();
	}

	@Bean
	Binding scansBinding(Queue scansQueue, DirectExchange scansExchange,
			@Value("${app.scans.routing-key}") String routingKey) {
		return BindingBuilder.bind(scansQueue).to(scansExchange).with(routingKey);
	}

	@Bean
	DirectExchange scansDeadLetterExchange(@Value("${app.scans.dead-letter-exchange}") String name) {
		return new DirectExchange(name);
	}

	/** Failed scans wait here, with RabbitMQ's x-death header saying why, to be inspected or replayed. */
	@Bean
	Queue scansDeadLetterQueue(@Value("${app.scans.dead-letter-queue}") String name) {
		return QueueBuilder.durable(name).build();
	}

	@Bean
	Binding scansDeadLetterBinding(Queue scansDeadLetterQueue, DirectExchange scansDeadLetterExchange) {
		return BindingBuilder.bind(scansDeadLetterQueue).to(scansDeadLetterExchange).with(scansDeadLetterQueue.getName());
	}

	/** Picked up by Spring Boot's listener retry; replaces the default reject so the failure reason is kept. */
	@Bean
	MessageRecoverer scanRecoverer(AmqpTemplate amqpTemplate,
			@Value("${app.scans.dead-letter-exchange}") String deadLetterExchange,
			@Value("${app.scans.dead-letter-queue}") String deadLetterQueue) {
		return new FailedScanRecoverer(amqpTemplate, deadLetterExchange, deadLetterQueue);
	}

	@Bean
	MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}
}
