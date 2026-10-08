package com.spydrone.orthanc_scan_consumer.scan.deadletter;

import java.time.Instant;
import java.util.Map;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;

/**
 * Called by the listener's retry once a scan has failed every attempt: publishes it to the dead-letter
 * exchange with why it failed ({@code x-exception-message}, {@code x-exception-stacktrace}) and when
 * ({@link #FAILED_AT_HEADER}), then lets the original be acked.
 */
public class FailedScanRecoverer extends RepublishMessageRecoverer {

	/** ISO-8601 instant the scan was given up on. */
	public static final String FAILED_AT_HEADER = "x-failed-at";

	public FailedScanRecoverer(AmqpTemplate template, String deadLetterExchange, String deadLetterRoutingKey) {
		super(template, deadLetterExchange, deadLetterRoutingKey);
	}

	@Override
	protected Map<? extends String, ?> additionalHeaders(Message message, Throwable cause) {
		return Map.of(FAILED_AT_HEADER, Instant.now().toString());
	}
}
