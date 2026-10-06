package com.spydrone.orthanc_scan_consumer.lot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.lot.domain.Location;
import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotRepository;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.lot.history.LotStageEvent;
import com.spydrone.orthanc_scan_consumer.lot.history.LotStageEventRepository;
import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

/** Commands through the real repository, domain-event publishing and history recorder. */
@DataJpaTest
@Import({ LotCommandHandler.class, LotHistoryRecorder.class })
class LotCommandHandlerTest {

	private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

	@Autowired
	private LotCommandHandler handler;

	@Autowired
	private LotRepository lots;

	@Autowired
	private LotStageEventRepository history;

	@Test
	void firstScanCreatesLotAndRecordsHistory() {
		handler.handle(ApplyScan.from(scan("c1", "intake", "wafer-prep", "WAFER-PREP-001"), NOW));

		Lot lot = lots.findById("L1").orElseThrow();
		assertThat(lot.location()).isEqualTo(new Location("wafer-prep", "WAFER-PREP-001"));
		assertThat(lot.getStatus()).isEqualTo(LotStatus.ACTIVE);

		List<LotStageEvent> rows = history.findByLotIdOrderByOccurredAtDesc("L1");
		assertThat(rows).hasSize(1);
		LotStageEvent row = rows.get(0);
		assertThat(row.getClientId()).isEqualTo("c1");
		assertThat(row.getScanType()).isEqualTo(ScanType.TRANSITIONAL);
		assertThat(row.getUserName()).isEqualTo("u");
		assertThat(row.getNote()).isEqualTo("n");
		assertThat(row.getFromStage()).isNull();
		assertThat(row.getToStage()).isEqualTo("wafer-prep");
		assertThat(row.getToWipLocation()).isEqualTo("WAFER-PREP-001");
		assertThat(row.getRejectedReason()).isNull();
	}

	@Test
	void rejectedScanLeavesLotAndRecordsReason() {
		handler.handle(ApplyScan.from(scan("c1", "intake", "intake", "INTAKE-001"), NOW.minusSeconds(60)));
		handler.handle(new SetLotHold("L1", true, NOW.minusSeconds(30)));

		handler.handle(ApplyScan.from(scan("c2", "intake", "wafer-prep", null), NOW));

		Lot lot = lots.findById("L1").orElseThrow();
		assertThat(lot.location()).isEqualTo(new Location("intake", "INTAKE-001"));
		assertThat(lot.getUpdatedAt()).isEqualTo(NOW.minusSeconds(30));
		LotStageEvent row = history.findById("c2").orElseThrow();
		assertThat(row.getRejectedReason()).isEqualTo("LOT_ON_HOLD");
		assertThat(row.getFromStage()).isEqualTo("intake");
		assertThat(row.getToStage()).isEqualTo("intake");
		assertThat(row.getToWipLocation()).isEqualTo("INTAKE-001");
	}

	@Test
	void changesStatusAndHold() {
		handler.handle(ApplyScan.from(scan("c1", "intake", "intake", null), NOW.minusSeconds(60)));

		assertThat(handler.handle(new ChangeLotStatus("L1", LotStatus.CANCELED, NOW)).getStatus())
				.isEqualTo(LotStatus.CANCELED);
		assertThat(handler.handle(new SetLotHold("L1", true, NOW)).isOnHold()).isTrue();
		assertThat(handler.handle(new SetLotHold("L1", false, NOW)).isOnHold()).isFalse();
		assertThat(lots.findById("L1").orElseThrow().getStatus()).isEqualTo(LotStatus.CANCELED);
	}

	@Test
	void unknownLotIsNotFound() {
		assertStatus(() -> handler.handle(new ChangeLotStatus("nope", LotStatus.COMPLETE, NOW)), HttpStatus.NOT_FOUND);
		assertStatus(() -> handler.handle(new SetLotHold("nope", true, NOW)), HttpStatus.NOT_FOUND);
	}

	@Test
	void missingValuesAreBadRequests() {
		assertStatus(() -> handler.handle(new ChangeLotStatus("L1", null, NOW)), HttpStatus.BAD_REQUEST);
		assertStatus(() -> handler.handle(new SetLotHold("L1", null, NOW)), HttpStatus.BAD_REQUEST);
	}

	private static ScanRecord scan(String clientId, String stage, String destinationStage, String destinationWip) {
		return new ScanRecord(clientId, "u", stage, "L1", destinationStage, destinationWip, ScanType.INFORMATIONAL, "n");
	}

	private static void assertStatus(Runnable call, HttpStatus status) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(status));
	}
}
