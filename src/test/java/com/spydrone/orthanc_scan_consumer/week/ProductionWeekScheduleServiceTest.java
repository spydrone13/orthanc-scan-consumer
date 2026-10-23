package com.spydrone.orthanc_scan_consumer.week;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotDetails;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotRepository;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;
import com.spydrone.orthanc_scan_consumer.week.ScheduledWeekView.CapacityUsage;

/** The schedule against a real database: lots by contract ship date, matched to each week's capacity rules. */
@DataJpaTest
@Import({ ProductionWeekScheduleService.class, ProductionWeekService.class })
class ProductionWeekScheduleServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

	@Autowired
	private ProductionWeekScheduleService schedule;

	@Autowired
	private ProductionWeekService weeks;

	@Autowired
	private LotRepository lots;

	private int lotIds;

	@Test
	void lotsCountInTheWeekContainingTheirShipDate() {
		// 2027 week 2 runs Sat Jan 2 to Fri Jan 8.
		lot(LocalDate.of(2027, 1, 2), 10, ChipType.Z_SERIES, ProcessType.A);
		lot(LocalDate.of(2027, 1, 8), 20, ChipType.Z_SERIES, ProcessType.A);
		lot(LocalDate.of(2027, 1, 9), 40, ChipType.Z_SERIES, ProcessType.A);
		// Saturday before 2027's first Friday: 2027 week 1, not 2026.
		lot(LocalDate.of(2026, 12, 26), 5, ChipType.Z_SERIES, ProcessType.A);

		assertThat(schedule.scheduled(2027)).containsExactly(
				new ScheduledWeekView(1, 1, 5, List.of()),
				new ScheduledWeekView(2, 2, 30, List.of()),
				new ScheduledWeekView(3, 1, 40, List.of()));
		assertThat(schedule.scheduled(2026)).isEmpty();
	}

	@Test
	void onlyActiveLotsCountWhetherHeldOrNot() {
		LocalDate shipDate = LocalDate.of(2027, 1, 6);
		lot(shipDate, 10, ChipType.Z_SERIES, ProcessType.A);
		lot(shipDate, 20, ChipType.Z_SERIES, ProcessType.A).placeOnHold(NOW);
		lot(shipDate, 100, ChipType.Z_SERIES, ProcessType.A).changeStatus(LotStatus.CANCELED, NOW);
		lot(shipDate, 100, ChipType.Z_SERIES, ProcessType.A).changeStatus(LotStatus.COMPLETE, NOW);
		lot(shipDate, 100, ChipType.Z_SERIES, ProcessType.A).changeStatus(LotStatus.DESTROYED, NOW);
		lots.flush();

		assertThat(schedule.scheduled(2027)).containsExactly(new ScheduledWeekView(2, 2, 30, List.of()));
	}

	@Test
	void eachRuleCountsTheLotsItMatches() {
		weeks.createYear(2027);
		weeks.setCapacities(2027, 2, List.of(
				new CapacityEntry(null, null, 1200),
				new CapacityEntry(ChipType.Z_SERIES, null, 500),
				new CapacityEntry(null, ProcessType.B, 300),
				new CapacityEntry(ChipType.Z_SERIES, ProcessType.A, 100)));
		LocalDate shipDate = LocalDate.of(2027, 1, 6);
		lot(shipDate, 150, ChipType.Z_SERIES, ProcessType.A);
		lot(shipDate, 400, ChipType.Z_SERIES, ProcessType.B);
		lot(shipDate, 70, ChipType.H_SERIES, ProcessType.B);
		// No chip type: only rules that don't name one. No wafer count: a lot, but no wafers.
		lot(shipDate, 30, null, ProcessType.B);
		lot(shipDate, null, ChipType.Z_SERIES, ProcessType.A);

		assertThat(schedule.scheduled(2027)).containsExactly(new ScheduledWeekView(2, 5, 650, List.of(
				new CapacityUsage(null, null, 1200, 650),
				new CapacityUsage(ChipType.Z_SERIES, null, 500, 550),
				new CapacityUsage(null, ProcessType.B, 300, 500),
				new CapacityUsage(ChipType.Z_SERIES, ProcessType.A, 100, 150))));
	}

	@Test
	void weeksWithRulesButNoLotsShowZeroScheduled() {
		weeks.createYear(2027);
		weeks.setCapacities(2027, 7, List.of(new CapacityEntry(ChipType.Q_SERIES, null, 50)));

		assertThat(schedule.scheduled(2027)).containsExactly(
				new ScheduledWeekView(7, 0, 0, List.of(new CapacityUsage(ChipType.Q_SERIES, null, 50, 0))));
	}

	@Test
	void lotsWithoutAShipDateAreNotScheduled() {
		lot(null, 10, ChipType.Z_SERIES, ProcessType.A);

		assertThat(schedule.scheduled(2027)).isEmpty();
	}

	@Test
	void yearsOutOfRangeAreBadRequests() {
		assertThatThrownBy(() -> schedule.scheduled(1999)).isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	private Lot lot(LocalDate shipDate, Integer wafers, ChipType chipType, ProcessType processType) {
		Lot lot = Lot.registered("L" + ++lotIds);
		lot.changeDetails(new LotDetails(shipDate, wafers, chipType, processType), NOW);
		return lots.save(lot);
	}
}
