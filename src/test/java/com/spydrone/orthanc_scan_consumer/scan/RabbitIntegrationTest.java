package com.spydrone.orthanc_scan_consumer.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import com.spydrone.orthanc_scan_consumer.lot.application.LotCommandHandler;
import com.spydrone.orthanc_scan_consumer.lot.application.command.ApplyScan;
import com.spydrone.orthanc_scan_consumer.lot.application.query.LotQueries;
import com.spydrone.orthanc_scan_consumer.lot.application.query.LotView;
import com.spydrone.orthanc_scan_consumer.scan.deadletter.DeadLetter;
import com.spydrone.orthanc_scan_consumer.scan.deadletter.DeadLetterService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The scan and lot-event messaging against a real RabbitMQ: topology, JSON conversion, retries ending in
 * the dead-letter queue, replay from it, and lot events on orthanc.lots. Skipped without Docker.
 * The context and broker are shared, so each test uses its own lot and scan ids.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:rabbit-it",
		"spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
		"spring.rabbitmq.listener.simple.retry.max-interval=200ms",
		"app.lots.outbox.relay-interval=200ms" })
@Testcontainers(disabledWithoutDocker = true)
class RabbitIntegrationTest {

	@Container
	@ServiceConnection
	static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4");

	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final JsonMapper JSON = new JsonMapper();

	@Autowired
	private RabbitTemplate rabbitTemplate;
	@Autowired
	private AmqpAdmin amqpAdmin;
	@Autowired
	private ScanRepository scans;
	@Autowired
	private LotQueries lots;
	@Autowired
	private DeadLetterService deadLetters;
	@MockitoSpyBean
	private LotCommandHandler lotCommands;

	@Value("${app.scans.exchange}")
	private String scansExchange;
	@Value("${app.scans.routing-key}")
	private String scansRoutingKey;
	@Value("${app.lots.exchange}")
	private String lotsExchange;

	/** Scans for this lot fail every attempt, as if applying them threw. */
	private volatile String failingLotId;
	/** Bound to orthanc.lots with lot.#, as a subscribing application would be. */
	private String lotEventsQueue;

	@BeforeEach
	void setUp() {
		failingLotId = null;
		willAnswer(invocation -> {
			ApplyScan command = invocation.getArgument(0);
			if (command.lotId().equals(failingLotId)) {
				throw new IllegalStateException("boom");
			}
			return invocation.callRealMethod();
		}).given(lotCommands).handle(any(ApplyScan.class));

		// Not exclusive (it would go when the connection that declared it is replaced), so durable:
		// RabbitMQ 4.3 refuses transient non-exclusive queues. Deleted after each test.
		Queue queue = QueueBuilder.durable("orthanc.lots.it-" + UUID.randomUUID()).build();
		amqpAdmin.declareQueue(queue);
		amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(lotsExchange)).with("lot.#"));
		lotEventsQueue = queue.getName();
	}

	@AfterEach
	void deleteLotEventsQueue() {
		amqpAdmin.deleteQueue(lotEventsQueue);
	}

	@Test
	void scanPublishedToTheScansExchangeIsStoredAndMovesTheLot() {
		ScanRecord scan = moveToWaferPrep(uniqueId("LOT"));

		publish(scan);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(scans.findById(scan.clientId())).isPresent());
		LotView lot = lots.get(scan.lotId());
		assertThat(lot.currentStage()).isEqualTo("wafer-prep");
		assertThat(lot.wipLocation()).isEqualTo("WAFER-PREP-001");
		assertThat(lot.lastScan().clientId()).isEqualTo(scan.clientId());
	}

	@Test
	void lotChangeIsPublishedToTheLotsExchange() {
		ScanRecord scan = moveToWaferPrep(uniqueId("LOT"));

		publish(scan);

		Message event = nextLotEvent(scan.lotId(), TIMEOUT).orElseThrow(() -> new AssertionError("No lot event received"));
		MessageProperties properties = event.getMessageProperties();
		assertThat(properties.getType()).isEqualTo("lot.moved");
		assertThat(properties.getContentType()).isEqualTo("application/json");
		assertThat(properties.getMessageId()).isNotBlank();
		JsonNode body = JSON.readTree(event.getBody());
		assertThat(body.get("eventId").asString()).isEqualTo(properties.getMessageId());
		// The lot's first scan, so it came from nowhere.
		assertThat(body.get("from").get("stage").isNull()).isTrue();
		assertThat(body.get("to").get("stage").asString()).isEqualTo("wafer-prep");
		assertThat(body.get("lot").get("wipLocation").asString()).isEqualTo("WAFER-PREP-001");
		assertThat(body.get("scan").get("clientId").asString()).isEqualTo(scan.clientId());
	}

	@Test
	void redeliveredScanIsAppliedOnce() {
		ScanRecord scan = moveToWaferPrep(uniqueId("LOT"));
		ScanRecord marker = moveToWaferPrep(uniqueId("LOT"));

		publish(scan);
		publish(scan);
		// One listener consumes in order, so once the marker is stored the duplicate has been handled.
		publish(marker);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(scans.findById(marker.clientId())).isPresent());
		assertThat(lots.history(scan.lotId())).hasSize(1);
		assertThat(nextLotEvent(scan.lotId(), TIMEOUT)).isPresent();
		assertThat(nextLotEvent(scan.lotId(), Duration.ofSeconds(2))).isEmpty();
	}

	@Test
	void scanThatFailsEveryRetryIsDeadLetteredThenAppliedWhenRetried() {
		ScanRecord scan = moveToWaferPrep(uniqueId("LOT"));
		failingLotId = scan.lotId();

		publish(scan);

		DeadLetter deadLetter = await().atMost(TIMEOUT)
				.until(() -> findDeadLetter(scan.clientId()), Optional::isPresent)
				.orElseThrow();
		assertThat(deadLetter.lotId()).isEqualTo(scan.lotId());
		assertThat(deadLetter.reason()).contains("boom");
		assertThat(deadLetter.stackTrace()).contains("IllegalStateException");
		assertThat(deadLetter.failedAt()).isNotNull();
		// Each attempt rolled back, and there were two retries after the first attempt.
		assertThat(scans.findById(scan.clientId())).isEmpty();
		verify(lotCommands, times(3)).handle(argThat((ApplyScan command) -> command.lotId().equals(scan.lotId())));

		failingLotId = null;
		assertThat(deadLetters.retry(d -> d.id().equals(scan.clientId()))).isEqualTo(1);

		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(scans.findById(scan.clientId())).isPresent());
		assertThat(lots.get(scan.lotId()).currentStage()).isEqualTo("wafer-prep");
		assertThat(findDeadLetter(scan.clientId())).isEmpty();
	}

	@Test
	void unreadableMessageIsDeadLettered() {
		String body = "not json " + UUID.randomUUID();
		MessageProperties properties = new MessageProperties();
		properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);

		rabbitTemplate.send(scansExchange, scansRoutingKey,
				new Message(body.getBytes(StandardCharsets.UTF_8), properties));

		DeadLetter deadLetter = await().atMost(TIMEOUT)
				.until(() -> deadLetters.list().deadLetters().stream()
						.filter(d -> d.body().equals(body))
						.findFirst(), Optional::isPresent)
				.orElseThrow();
		assertThat(deadLetter.id()).startsWith("sha-");
		assertThat(deadLetter.clientId()).isNull();
		assertThat(deadLetter.reason()).isNotBlank();
	}

	/** The seeded intake stage allows wafer-prep next, so this is an on-route move. */
	private static ScanRecord moveToWaferPrep(String lotId) {
		return new ScanRecord(uniqueId("scan"), "op1", "intake", lotId, "wafer-prep", "WAFER-PREP-001",
				ScanType.TRANSITIONAL, "", null, null);
	}

	/** As the producer publishes: the record as JSON through the scans exchange. */
	private void publish(ScanRecord scan) {
		rabbitTemplate.convertAndSend(scansExchange, scansRoutingKey, scan);
	}

	/**
	 * The next event for this lot on the test's lot-events queue within {@code wait}, dropping events for
	 * other lots (a previous test's events can still be relayed after this queue is bound).
	 */
	private Optional<Message> nextLotEvent(String lotId, Duration wait) {
		long deadline = System.nanoTime() + wait.toNanos();
		while (System.nanoTime() < deadline) {
			Message message = rabbitTemplate.receive(lotEventsQueue, 200);
			if (message != null && lotId.equals(JSON.readTree(message.getBody()).get("lotId").asString())) {
				return Optional.of(message);
			}
		}
		return Optional.empty();
	}

	private Optional<DeadLetter> findDeadLetter(String id) {
		return deadLetters.list().deadLetters().stream().filter(d -> id.equals(d.id())).findFirst();
	}

	private static String uniqueId(String prefix) {
		return prefix + "-" + UUID.randomUUID();
	}
}
