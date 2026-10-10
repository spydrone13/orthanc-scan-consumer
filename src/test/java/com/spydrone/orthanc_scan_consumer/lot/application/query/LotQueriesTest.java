package com.spydrone.orthanc_scan_consumer.lot.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.spydrone.orthanc_scan_consumer.lot.application.LotCommandHandler;
import com.spydrone.orthanc_scan_consumer.lot.application.LotNotFoundException;
import com.spydrone.orthanc_scan_consumer.lot.application.command.ApplyScan;
import com.spydrone.orthanc_scan_consumer.lot.application.command.SetLotHold;
import com.spydrone.orthanc_scan_consumer.lot.application.history.LotHistoryRecorder;
import com.spydrone.orthanc_scan_consumer.lot.domain.Discrepancy;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;
import com.spydrone.orthanc_scan_consumer.stage.LotStageRoutes;
import com.spydrone.orthanc_scan_consumer.stage.LotStageService;

/** The read side's JPQL projections against a real database. */
@DataJpaTest
@Import({ LotQueries.class, LotCommandHandler.class, LotHistoryRecorder.class, LotStageRoutes.class,
		LotStageService.class })
class LotQueriesTest {

	private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

	@Autowired
	private LotQueries queries;

	@Autowired
	private LotCommandHandler commands;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void listsViewsNewestFirst() {
		scan("c1", "L1", "intake", "intake", "INTAKE-001", NOW.minusSeconds(60));
		scan("c2", "L2", "wafer-prep", "wafer-prep", null, NOW);
		commands.handle(new SetLotHold("L1", true, NOW.minusSeconds(30)));

		assertThat(queries.list()).containsExactly(
				new LotView("L2", "wafer-prep", null, LotStatus.ACTIVE, false, NOW,
						new LotView.LastScan("c2", "u", NOW)),
				new LotView("L1", "intake", "INTAKE-001", LotStatus.ACTIVE, true, NOW.minusSeconds(30),
						new LotView.LastScan("c1", "u", NOW.minusSeconds(60))));
		assertThat(queries.get("L1").onHold()).isTrue();
	}

	@Test
	void lastScanIsTheScanThatMovedTheLotEvenAfterACorrection() {
		scan("c1", "L1", "photolithography", "dry-etching", null, NOW.minusSeconds(60));
		scan("c2", "L1", "wet-etching", "photoresist-removal", null, NOW);

		assertThat(queries.get("L1").lastScan()).isEqualTo(new LotView.LastScan("c2", "u", NOW));
	}

	@Test
	void historyIsNewestFirst() {
		scan("c1", "L1", "intake", "intake", null, NOW.minusSeconds(60));
		scan("c2", "L1", "intake", "wafer-prep", null, NOW);

		assertThat(queries.history("L1")).extracting(LotStageEventView::clientId).containsExactly("c2", "c1");
		assertThat(queries.history("L1").get(0).fromStage()).isEqualTo("intake");
	}

	@Test
	void exceptionsListFlaggedRowsWithTheScanTheyCorrect() {
		scan("c1", "L1", "photolithography", "dry-etching", null, NOW.minusSeconds(120));
		scan("c2", "L1", "wet-etching", "photoresist-removal", null, NOW.minusSeconds(60));
		scan("c3", "L2", "intake", "testing", null, NOW);

		List<LotExceptionView> exceptions = queries.exceptions(null);

		assertThat(exceptions).extracting(e -> e.event().clientId(), e -> e.event().exception())
				.containsExactly(
						tuple("c3", Discrepancy.OFF_ROUTE),
						tuple("c2#correction", Discrepancy.LOCATION_MISMATCH_UNCONFIRMED));
		LotExceptionView correction = exceptions.get(1);
		assertThat(correction.event().fromStage()).isEqualTo("dry-etching");
		assertThat(correction.event().toStage()).isEqualTo("wet-etching");
		assertThat(correction.corrects().clientId()).isEqualTo("c1");
		assertThat(exceptions.get(0).corrects()).isNull();

		assertThat(queries.exceptions(NOW.minusSeconds(30))).hasSize(1);
		assertThat(queries.history("L1")).extracting(LotStageEventView::clientId)
				.containsExactly("c2", "c2#correction", "c1");
	}

	@Test
	void historyRowsStoredWithoutAScanTypeStillRead() {
		scan("c1", "L1", "intake", "intake", null, NOW);
		jdbc.update("insert into lot_stage_events (client_id, lot_id, occurred_at) values (?, ?, ?)", "old", "L1",
				Timestamp.from(NOW.minusSeconds(60)));

		assertThat(queries.history("L1")).extracting(LotStageEventView::scanType)
				.containsExactly(ScanType.INFORMATIONAL, null);
	}

	@Test
	void unknownLotIsNotFound() {
		assertThatThrownBy(() -> queries.get("nope")).isInstanceOf(LotNotFoundException.class);
		assertThatThrownBy(() -> queries.history("nope")).isInstanceOf(LotNotFoundException.class);
	}

	private void scan(String clientId, String lotId, String stage, String destinationStage, String wip,
			Instant at) {
		commands.handle(ApplyScan.from(
				new ScanRecord(clientId, "u", stage, lotId, destinationStage, wip, ScanType.INFORMATIONAL, "", null, null),
				at));
	}
}
