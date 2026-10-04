package com.spydrone.orthanc_scan_consumer.scan;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mirrors the producer's declarations so either app can start first. */
@Configuration
public class RabbitConfig {

	@Bean
	DirectExchange scansExchange(@Value("${app.scans.exchange}") String name) {
		return new DirectExchange(name);
	}

	@Bean
	Queue scansQueue(@Value("${app.scans.queue}") String name) {
		return new Queue(name, true);
	}

	@Bean
	Binding scansBinding(Queue scansQueue, DirectExchange scansExchange,
			@Value("${app.scans.routing-key}") String routingKey) {
		return BindingBuilder.bind(scansQueue).to(scansExchange).with(routingKey);
	}

	@Bean
	MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}
}
