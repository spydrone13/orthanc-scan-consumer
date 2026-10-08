package com.spydrone.orthanc_scan_consumer.scan.deadletter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;

import com.rabbitmq.client.AMQP;
import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A scan waiting in the dead-letter queue.
 *
 * @param id the scan's clientId; for a message that can't be read as a scan, "sha-" and the start of
 *        its body's SHA-256, so it can still be retried or discarded on its own
 * @param clientId null when the body can't be read as a scan, as are lotId and userName
 * @param reason the exception message, or RabbitMQ's dead-letter reason (e.g. "rejected")
 * @param stackTrace null unless the scan failed every retry
 * @param failedAt null if the message carries no time
 * @param body the message as received, as text
 */
public record DeadLetter(String id, String clientId, String lotId, String userName, String reason,
		String stackTrace, Instant failedAt, String body) {

	static DeadLetter of(AMQP.BasicProperties properties, byte[] body, JsonMapper json) {
		Map<String, Object> headers = properties.getHeaders() == null ? Map.of() : properties.getHeaders();
		Map<?, ?> death = firstDeath(headers);
		Map<?, ?> scan = readObject(body, json);
		String clientId = text(scan, "clientId");

		String reason = header(headers, RepublishMessageRecoverer.X_EXCEPTION_MESSAGE);
		if (reason == null && death != null && death.get("reason") != null) {
			reason = String.valueOf(death.get("reason"));
		}
		return new DeadLetter(
				clientId != null ? clientId : "sha-" + sha256(body).substring(0, 16),
				clientId,
				text(scan, "lotId"),
				text(scan, "userName"),
				reason,
				header(headers, RepublishMessageRecoverer.X_EXCEPTION_STACKTRACE),
				failedAt(headers, death, properties),
				new String(body, StandardCharsets.UTF_8));
	}

	/**
	 * Read as a plain JSON object rather than a {@link ScanRecord}, so a scan that failed because of a
	 * bad field (an unknown scan type, say) still shows its clientId and lot.
	 */
	private static Map<?, ?> readObject(byte[] body, JsonMapper json) {
		try {
			return json.readValue(body, Map.class);
		}
		catch (JacksonException e) {
			return null;
		}
	}

	private static String text(Map<?, ?> object, String field) {
		return object != null && object.get(field) instanceof String value && !value.isBlank() ? value : null;
	}

	/** RabbitMQ's x-death header: one entry per queue the message was dead-lettered from, newest first. */
	private static Map<?, ?> firstDeath(Map<String, Object> headers) {
		return headers.get("x-death") instanceof List<?> deaths && !deaths.isEmpty()
				&& deaths.get(0) instanceof Map<?, ?> death ? death : null;
	}

	/** Header strings arrive from the broker as LongString, whose toString is the text. */
	private static String header(Map<String, Object> headers, String name) {
		Object value = headers.get(name);
		return value == null ? null : value.toString();
	}

	private static Instant failedAt(Map<String, Object> headers, Map<?, ?> death, AMQP.BasicProperties properties) {
		String failedAt = header(headers, FailedScanRecoverer.FAILED_AT_HEADER);
		if (failedAt != null) {
			try {
				return Instant.parse(failedAt);
			}
			catch (DateTimeParseException e) {
				// fall through to the broker's time
			}
		}
		if (death != null && death.get("time") instanceof Date time) {
			return time.toInstant();
		}
		return properties.getTimestamp() == null ? null : properties.getTimestamp().toInstant();
	}

	private static String sha256(byte[] body) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
