package com.spydrone.orthanc_scan_consumer.lot.application.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The exchange other applications subscribe to for lot events. Each subscriber declares and binds its own
 * queue (see README), so adding one needs no change here.
 */
@Configuration
@EnableScheduling
public class LotEventsConfig {

	@Bean
	TopicExchange lotsExchange(@Value("${app.lots.exchange}") String name) {
		return new TopicExchange(name);
	}
}
