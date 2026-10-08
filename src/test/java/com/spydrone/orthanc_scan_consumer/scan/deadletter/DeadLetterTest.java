package com.spydrone.orthanc_scan_consumer.scan.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.LongStringHelper;

import tools.jackson.databind.json.JsonMapper;

class DeadLetterTest {

	private static final JsonMapper JSON = new JsonMapper();
	private static final byte[] SCAN = """
			{"clientId":"abc","userName":"u","currentStage":"S1","lotId":"L1","scanType":"TRANSITIONAL"}"""
			.getBytes(StandardCharsets.UTF_8);

	@Test
	void scanThatFailedItsRetriesCarriesTheExceptionAndTime() {
		AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().headers(Map.of(
				"x-exception-message", LongStringHelper.asLongString("Lot L1 exploded"),
				"x-exception-stacktrace", LongStringHelper.asLongString("java.lang.IllegalStateException: ..."),
				"x-failed-at", LongStringHelper.asLongString("2026-10-08T13:00:00Z"))).build();

		DeadLetter deadLetter = DeadLetter.of(properties, SCAN, JSON);

		assertThat(deadLetter.id()).isEqualTo("abc");
		assertThat(deadLetter.clientId()).isEqualTo("abc");
		assertThat(deadLetter.lotId()).isEqualTo("L1");
		assertThat(deadLetter.userName()).isEqualTo("u");
		assertThat(deadLetter.reason()).isEqualTo("Lot L1 exploded");
		assertThat(deadLetter.stackTrace()).startsWith("java.lang.IllegalStateException");
		assertThat(deadLetter.failedAt()).isEqualTo(Instant.parse("2026-10-08T13:00:00Z"));
		assertThat(deadLetter.body()).contains("\"clientId\":\"abc\"");
	}

	@Test
	void messageDeadLetteredByTheBrokerUsesXDeath() {
		Date time = Date.from(Instant.parse("2026-10-08T12:00:00Z"));
		AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().headers(Map.of("x-death",
				List.of(Map.of("reason", LongStringHelper.asLongString("rejected"), "time", time)))).build();

		DeadLetter deadLetter = DeadLetter.of(properties, SCAN, JSON);

		assertThat(deadLetter.reason()).isEqualTo("rejected");
		assertThat(deadLetter.failedAt()).isEqualTo(time.toInstant());
		assertThat(deadLetter.stackTrace()).isNull();
	}

	@Test
	void unreadableMessageIsIdentifiedByItsBody() {
		byte[] body = "not json".getBytes(StandardCharsets.UTF_8);

		DeadLetter deadLetter = DeadLetter.of(new AMQP.BasicProperties(), body, JSON);

		assertThat(deadLetter.clientId()).isNull();
		assertThat(deadLetter.lotId()).isNull();
		assertThat(deadLetter.id()).startsWith("sha-").hasSize(20)
				.isEqualTo(DeadLetter.of(new AMQP.BasicProperties(), body, JSON).id());
		assertThat(deadLetter.reason()).isNull();
		assertThat(deadLetter.failedAt()).isNull();
		assertThat(deadLetter.body()).isEqualTo("not json");
	}

	@Test
	void scanWithoutAClientIdIsIdentifiedByItsBody() {
		byte[] body = "{\"lotId\":\"L1\"}".getBytes(StandardCharsets.UTF_8);

		DeadLetter deadLetter = DeadLetter.of(new AMQP.BasicProperties(), body, JSON);

		assertThat(deadLetter.id()).startsWith("sha-");
		assertThat(deadLetter.lotId()).isEqualTo("L1");
	}
}
