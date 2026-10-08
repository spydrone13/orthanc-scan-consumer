package com.spydrone.orthanc_scan_consumer.scan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

class RabbitConfigTest {

	private final RabbitConfig config = new RabbitConfig();

	@Test
	void scansQueueDeadLettersToTheDeadLetterQueue() {
		Queue queue = config.scansQueue("orthanc.scans", "orthanc.scans.dlx", "orthanc.scans.dlq");

		assertThat(queue.isDurable()).isTrue();
		assertThat(queue.getArguments())
				.containsEntry("x-dead-letter-exchange", "orthanc.scans.dlx")
				.containsEntry("x-dead-letter-routing-key", "orthanc.scans.dlq");
	}

	@Test
	void deadLetterQueueIsBoundToTheDeadLetterExchangeByItsName() {
		Queue dlq = config.scansDeadLetterQueue("orthanc.scans.dlq");
		DirectExchange dlx = config.scansDeadLetterExchange("orthanc.scans.dlx");

		Binding binding = config.scansDeadLetterBinding(dlq, dlx);

		assertThat(dlq.isDurable()).isTrue();
		assertThat(binding.getExchange()).isEqualTo("orthanc.scans.dlx");
		assertThat(binding.getDestination()).isEqualTo("orthanc.scans.dlq");
		assertThat(binding.getRoutingKey()).isEqualTo("orthanc.scans.dlq");
	}
}
