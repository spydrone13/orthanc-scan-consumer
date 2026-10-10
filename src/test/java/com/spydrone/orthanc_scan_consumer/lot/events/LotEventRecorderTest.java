package com.spydrone.orthanc_scan_consumer.lot.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;

import com.spydrone.orthanc_scan_consumer.lot.application.ApplyScan;
import com.spydrone.orthanc_scan_consumer.lot.application.ChangeLotStatus;
import com.spydrone.orthanc_scan_consumer.lot.application.LotCommandHandler;
import com.spydrone.orthanc_scan_consumer.lot.application.SetLotHold;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.lot.events.LotEventMessage.LotState;
import com.spydrone.orthanc_scan_consumer.lot.events.LotEventMessage.Place;
import com.spydrone.orthanc_scan_consumer.lot.events.LotEventMessage.ScanInfo;
import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;
import com.spydrone.orthanc_scan_consumer.stage.LotStageRoutes;
import com.spydrone.orthanc_scan_consumer.stage.LotStageService;

import tools.jackson.databind.json.JsonMapper;

/** Commands through the real repository and domain-event publishing, into the outbox. */
@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({ LotCommandHandler.class, LotEventRecorder.class, LotStageRoutes.class, LotStageService.class })
class LotEventRecorderTest {

	private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

	@Autowired
	private LotCommandHandler handler;

	@Autowired
	private OutboxEventRepository outbox;

	@Autowired
	private JsonMapper json;

	@Test
	void moveIsRecordedWithTheLotsNewStateAndTheScan() {
		handler.handle(ApplyScan.from(scan("c1", "intake", "wafer-prep", "WAFER-PREP-001"), NOW));

		OutboxEvent row = only(rows());
		assertThat(row.getRoutingKey()).isEqualTo(LotEventMessage.MOVED);
		assertThat(row.getLotId()).isEqualTo("L1");
		assertThat(row.getPublishedAt()).isNull();
		LotEventMessage message = message(row);
		assertThat(message.eventId()).isEqualTo(row.getEventId()).hasSize(36);
		assertThat(message.type()).isEqualTo(LotEventMessage.MOVED);
		assertThat(message.schemaVersion()).isEqualTo(1);
		assertThat(message.sequence()).isNull();
		assertThat(message.occurredAt()).isEqualTo(NOW);
		assertThat(message.lot()).isEqualTo(new LotState("wafer-prep", "WAFER-PREP-001", LotStatus.ACTIVE, false));
		assertThat(message.from()).isEqualTo(new Place(null, null));
		assertThat(message.to()).isEqualTo(new Place("wafer-prep", "WAFER-PREP-001"));
		assertThat(message.scan()).isEqualTo(new ScanInfo("c1", "u", "intake", "n"));
		assertThat(message.discrepancy()).isNull();
	}

	@Test
	void scanThatLeavesTheLotWhereItIsIsScanned() {
		handler.handle(ApplyScan.from(scan("c1", "intake", "intake", "INTAKE-001"), NOW.minusSeconds(60)));

		handler.handle(ApplyScan.from(scan("c2", "intake", null, null), NOW));

		assertThat(types()).containsExactly(LotEventMessage.MOVED, LotEventMessage.SCANNED);
	}

	@Test
	void correctionComesBeforeTheScanThatCausedIt() {
		handler.handle(ApplyScan.from(scan("c1", "intake", "intake", "INTAKE-001"), NOW.minusSeconds(60)));

		handler.handle(ApplyScan.from(scan("c2", "wafer-prep", "wafer-prep", "WAFER-PREP-001"), NOW));

		assertThat(types()).containsExactly(LotEventMessage.MOVED, LotEventMessage.LOCATION_CORRECTED,
				LotEventMessage.MOVED);
		LotEventMessage correction = message(rows().get(1));
		assertThat(correction.from()).isEqualTo(new Place("intake", "INTAKE-001"));
		assertThat(correction.to()).isEqualTo(new Place("wafer-prep", null));
		assertThat(correction.lot().currentStage()).isEqualTo("wafer-prep");
		assertThat(correction.discrepancy()).isEqualTo("LOCATION_MISMATCH_UNCONFIRMED");
	}

	@Test
	void rejectedScanIsRecordedWithWhereTheLotStayed() {
		handler.handle(ApplyScan.from(scan("c1", "intake", "intake", "INTAKE-001"), NOW.minusSeconds(60)));
		handler.handle(new SetLotHold("L1", true, NOW.minusSeconds(30)));

		handler.handle(ApplyScan.from(scan("c2", "intake", "wafer-prep", null), NOW));

		assertThat(types()).containsExactly(LotEventMessage.MOVED, LotEventMessage.HOLD_CHANGED,
				LotEventMessage.SCAN_REJECTED);
		LotEventMessage rejected = message(rows().get(2));
		assertThat(rejected.rejectionReason()).isEqualTo("LOT_ON_HOLD");
		assertThat(rejected.from()).isNull();
		assertThat(rejected.to()).isEqualTo(new Place("intake", "INTAKE-001"));
		assertThat(rejected.lot()).isEqualTo(new LotState("intake", "INTAKE-001", LotStatus.ACTIVE, true));
	}

	@Test
	void statusAndHoldChangesAreRecordedOnlyWhenSomethingChanges() {
		handler.handle(ApplyScan.from(scan("c1", "intake", "intake", "INTAKE-001"), NOW.minusSeconds(60)));

		handler.handle(new ChangeLotStatus("L1", LotStatus.COMPLETE, NOW));
		handler.handle(new ChangeLotStatus("L1", LotStatus.COMPLETE, NOW));
		handler.handle(new SetLotHold("L1", true, NOW));
		handler.handle(new SetLotHold("L1", true, NOW));

		assertThat(types()).containsExactly(LotEventMessage.MOVED, LotEventMessage.STATUS_CHANGED,
				LotEventMessage.HOLD_CHANGED);
		LotEventMessage status = message(rows().get(1));
		assertThat(status.previousStatus()).isEqualTo(LotStatus.ACTIVE);
		// Spelled as in the lot API, so subscribers that load GET /api/lots see the same values.
		assertThat(rows().get(1).getPayload()).contains("\"status\":\"complete\"").contains("\"previousStatus\":\"active\"");
		assertThat(status.lot()).isEqualTo(new LotState("intake", "INTAKE-001", LotStatus.COMPLETE, false));
		assertThat(status.scan()).isNull();
		assertThat(message(rows().get(2)).lot().onHold()).isTrue();
	}

	/** In the order they were recorded. */
	private List<OutboxEvent> rows() {
		return outbox.findAll(Sort.by("id"));
	}

	private List<String> types() {
		return rows().stream().map(OutboxEvent::getRoutingKey).toList();
	}

	private LotEventMessage message(OutboxEvent row) {
		return json.readValue(row.getPayload(), LotEventMessage.class);
	}

	private static OutboxEvent only(List<OutboxEvent> rows) {
		assertThat(rows).hasSize(1);
		return rows.get(0);
	}

	private static ScanRecord scan(String clientId, String stage, String destinationStage, String destinationWip) {
		return new ScanRecord(clientId, "u", stage, "L1", destinationStage, destinationWip, ScanType.INFORMATIONAL, "n",
				null, null);
	}
}
