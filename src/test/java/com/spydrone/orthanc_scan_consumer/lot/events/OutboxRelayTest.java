package com.spydrone.orthanc_scan_consumer.lot.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;

import tools.jackson.databind.json.JsonMapper;

class OutboxRelayTest {

	private static final Instant AT = Instant.parse("2026-10-08T12:00:00Z");
	private static final JsonMapper JSON = new JsonMapper();

	private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
	private final Channel channel = mock(Channel.class);
	private final OutboxRelay relay = new OutboxRelay(outbox, connectionFactory(channel), JSON, "orthanc.lots", 100,
			Duration.ofSeconds(10), Duration.ofDays(7));

	private OutboxEvent first;
	private OutboxEvent second;

	@BeforeEach
	void pending() {
		first = event(7L, "e-7", LotEventMessage.MOVED);
		second = event(8L, "e-8", LotEventMessage.HOLD_CHANGED);
		given(outbox.findByPublishedAtIsNullOrderByIdAsc(Limit.of(100))).willReturn(List.of(first, second));
	}

	@Test
	void publishesInOrderWithConfirmsThenMarksPublished() throws Exception {
		assertThat(relay.publishPending()).isEqualTo(2);

		InOrder order = inOrder(channel, outbox);
		order.verify(channel).confirmSelect();
		ArgumentCaptor<AMQP.BasicProperties> properties = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
		ArgumentCaptor<byte[]> bodies = ArgumentCaptor.forClass(byte[].class);
		order.verify(channel).basicPublish(eq("orthanc.lots"), eq(LotEventMessage.MOVED), properties.capture(),
				bodies.capture());
		order.verify(channel).basicPublish(eq("orthanc.lots"), eq(LotEventMessage.HOLD_CHANGED), properties.capture(),
				bodies.capture());
		order.verify(channel).waitForConfirmsOrDie(10_000);
		order.verify(outbox).saveAll(List.of(first, second));

		AMQP.BasicProperties props = properties.getAllValues().get(0);
		assertThat(props.getMessageId()).isEqualTo("e-7");
		assertThat(props.getType()).isEqualTo(LotEventMessage.MOVED);
		assertThat(props.getContentType()).isEqualTo("application/json");
		assertThat(props.getDeliveryMode()).isEqualTo(2);
		assertThat(props.getTimestamp().toInstant()).isEqualTo(AT);

		LotEventMessage sent = JSON.readValue(new String(bodies.getAllValues().get(0), StandardCharsets.UTF_8),
				LotEventMessage.class);
		assertThat(sent.eventId()).isEqualTo("e-7");
		assertThat(sent.sequence()).isEqualTo(7L);
		assertThat(first.getPublishedAt()).isNotNull();
		assertThat(second.getPublishedAt()).isNotNull();
	}

	@Test
	void unconfirmedEventsStayUnpublished() throws Exception {
		willThrow(new IOException("nack")).given(channel).waitForConfirmsOrDie(anyLong());

		assertThat(relay.publishPending()).isZero();

		verify(outbox, never()).saveAll(any());
		assertThat(first.getPublishedAt()).isNull();
	}

	@Test
	void channelClosedByTheBrokerLeavesEventsUnpublished() throws Exception {
		willThrow(new ShutdownSignalException(false, false, null, channel)).given(channel)
				.waitForConfirmsOrDie(anyLong());

		assertThat(relay.publishPending()).isZero();

		verify(outbox, never()).saveAll(any());
		assertThat(first.getPublishedAt()).isNull();
	}

	@Test
	void unreachableBrokerLeavesEventsUnpublished() throws Exception {
		ConnectionFactory down = mock(ConnectionFactory.class);
		given(down.createConnection()).willThrow(new org.springframework.amqp.AmqpConnectException(new IOException("down")));
		OutboxRelay relayToDownBroker = new OutboxRelay(outbox, down, JSON, "orthanc.lots", 100, Duration.ofSeconds(10),
				Duration.ofDays(7));

		assertThat(relayToDownBroker.publishPending()).isZero();

		verify(outbox, never()).saveAll(any());
	}

	@Test
	void nothingPendingPublishesNothing() throws Exception {
		given(outbox.findByPublishedAtIsNullOrderByIdAsc(Limit.of(100))).willReturn(List.of());

		assertThat(relay.publishPending()).isZero();

		verify(channel, never()).basicPublish(any(), any(), anyBoolean(), any(), any());
		verify(channel, never()).basicPublish(any(), any(), any(), any());
	}

	@Test
	void deletesEventsPublishedBeforeTheRetentionPeriod() {
		relay.deleteOldPublished();

		ArgumentCaptor<Instant> before = ArgumentCaptor.forClass(Instant.class);
		verify(outbox).deletePublishedBefore(before.capture());
		assertThat(before.getValue()).isBetween(Instant.now().minus(Duration.ofDays(7)).minusSeconds(5),
				Instant.now().minus(Duration.ofDays(7)));
	}

	private static OutboxEvent event(long id, String eventId, String type) {
		LotEventMessage message = new LotEventMessage(eventId, type, 1, null, AT, "L1",
				new LotEventMessage.LotState("intake", null, LotStatus.ACTIVE, false), null, null, null, null, null, null);
		OutboxEvent event = new OutboxEvent(eventId, "L1", type, JSON.writeValueAsString(message), AT);
		ReflectionTestUtils.setField(event, "id", id);
		return event;
	}

	private static ConnectionFactory connectionFactory(Channel channel) {
		ConnectionFactory factory = mock(ConnectionFactory.class);
		Connection connection = mock(Connection.class);
		given(factory.createConnection()).willReturn(connection);
		given(connection.createChannel(false)).willReturn(channel);
		return factory;
	}
}
