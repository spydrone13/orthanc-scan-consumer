package com.spydrone.orthanc_scan_consumer.lot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

/** The read side's JPQL projections against a real database. */
@DataJpaTest
@Import({ LotQueries.class, LotCommandHandler.class, LotHistoryRecorder.class })
class LotQueriesTest {

	private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

	@Autowired
	private LotQueries queries;

	@Autowired
	private LotCommandHandler commands;

	@Test
	void listsViewsNewestFirst() {
		scan("c1", "L1", "intake", "INTAKE-001", NOW.minusSeconds(60));
		scan("c2", "L2", "wafer-prep", null, NOW);
		commands.handle(new SetLotHold("L1", true, NOW.minusSeconds(30)));

		assertThat(queries.list()).containsExactly(
				new LotView("L2", "wafer-prep", null, LotStatus.ACTIVE, false, NOW),
				new LotView("L1", "intake", "INTAKE-001", LotStatus.ACTIVE, true, NOW.minusSeconds(30)));
		assertThat(queries.get("L1").onHold()).isTrue();
	}

	@Test
	void historyIsNewestFirst() {
		scan("c1", "L1", "intake", null, NOW.minusSeconds(60));
		scan("c2", "L1", "wafer-prep", null, NOW);

		assertThat(queries.history("L1")).extracting(LotStageEventView::clientId).containsExactly("c2", "c1");
		assertThat(queries.history("L1").get(0).fromStage()).isEqualTo("intake");
	}

	@Test
	void unknownLotIsNotFound() {
		assertThatThrownBy(() -> queries.get("nope")).isInstanceOf(LotNotFoundException.class);
		assertThatThrownBy(() -> queries.history("nope")).isInstanceOf(LotNotFoundException.class);
	}

	private void scan(String clientId, String lotId, String stage, String wip, Instant at) {
		commands.handle(ApplyScan.from(
				new ScanRecord(clientId, "u", stage, lotId, stage, wip, ScanType.INFORMATIONAL, ""), at));
	}
}
