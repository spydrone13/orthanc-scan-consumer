package com.spydrone.orthanc_scan_consumer.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

import com.spydrone.orthanc_scan_consumer.scan.deadletter.FailedScanRecoverer;

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

	@Test
	void scanThatFailsEveryRetryIsRepublishedToTheDeadLetterQueueWithTheReason() {
		AmqpTemplate template = mock(AmqpTemplate.class);
		MessageRecoverer recoverer = config.scanRecoverer(template, "orthanc.scans.dlx", "orthanc.scans.dlq");
		Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());

		recoverer.recover(message, new IllegalStateException("boom"));

		ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
		verify(template).send(eq("orthanc.scans.dlx"), eq("orthanc.scans.dlq"), sent.capture());
		assertThat(sent.getValue().getMessageProperties().<String>getHeader("x-exception-message")).isEqualTo("boom");
		assertThat(sent.getValue().getMessageProperties().<String>getHeader(FailedScanRecoverer.FAILED_AT_HEADER))
				.isNotNull();
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void scanThatFailsEveryRetryIsLoggedWithItsStackTrace(CapturedOutput output) {
		MessageRecoverer recoverer = config.scanRecoverer(mock(AmqpTemplate.class), "orthanc.scans.dlx",
				"orthanc.scans.dlq");
		Message message = new Message("{\"clientId\":\"abc\"}".getBytes(StandardCharsets.UTF_8),
				new MessageProperties());

		recoverer.recover(message, new IllegalStateException("boom"));

		assertThat(output).contains("ERROR")
				.contains("Scan failed after all retries; moving it to the dead-letter queue: {\"clientId\":\"abc\"}")
				.contains("java.lang.IllegalStateException: boom")
				.contains("at com.spydrone.orthanc_scan_consumer.scan.RabbitConfigTest");
	}
}
