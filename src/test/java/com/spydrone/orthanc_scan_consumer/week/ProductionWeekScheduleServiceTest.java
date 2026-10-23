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

import com.spydrone.orthanc_scan_consumer.lot.application.query.LotQueries;
import com.spydrone.orthanc_scan_consumer.lot.application.query.LotView;
import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotDetails;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotRepository;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;
import com.spydrone.orthanc_scan_consumer.week.ScheduleCheckView.CapacityCheck;
import com.spydrone.orthanc_scan_consumer.week.ScheduleCheckView.LotCandidate;
import com.spydrone.orthanc_scan_consumer.week.ScheduleCheckView.Reason;
import com.spydrone.orthanc_scan_consumer.week.ScheduledWeekView.CapacityUsage;

/** The schedule against a real database: lots by contract ship date, matched to each week's capacity rules. */
@DataJpaTest
@Import({ ProductionWeekScheduleService.class, ProductionWeekService.class, LotQueries.class })
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

	// --- schedule check. 2027 week 2 runs Sat Jan 2 to Fri Jan 8.

	/** Week 2 with a total, a Z_SERIES and a Q_SERIES rule, and 400 Z_SERIES/A wafers already scheduled. */
	private void weekTwoWithRules() {
		weeks.createYear(2027);
		weeks.setCapacities(2027, 2, List.of(
				new CapacityEntry(null, null, 1000),
				new CapacityEntry(ChipType.Z_SERIES, null, 500),
				new CapacityEntry(ChipType.Q_SERIES, null, 0)));
		lot(LocalDate.of(2027, 1, 6), 400, ChipType.Z_SERIES, ProcessType.A);
	}

	@Test
	void lotThatFitsCanBeScheduledAndOnlyItsRulesAreChecked() {
		weekTwoWithRules();

		ScheduleCheckView check = schedule.check(2027, 2, null, 100, ChipType.Z_SERIES, ProcessType.B);

		assertThat(check.canSchedule()).isTrue();
		assertThat(check.reasons()).isEmpty();
		assertThat(check.lot()).isEqualTo(new LotCandidate(null, 100, ChipType.Z_SERIES, ProcessType.B, false));
		// Exactly reaching capacity fits; the full Q_SERIES rule doesn't apply to a Z_SERIES lot.
		assertThat(check.capacities()).containsExactly(
				new CapacityCheck(null, null, 1000, 400, 500, true),
				new CapacityCheck(ChipType.Z_SERIES, null, 500, 400, 500, true));
	}

	@Test
	void goingOverAnApplicableRuleIsOverCapacity() {
		weekTwoWithRules();

		ScheduleCheckView check = schedule.check(2027, 2, null, 101, ChipType.Z_SERIES, null);

		assertThat(check.canSchedule()).isFalse();
		assertThat(check.reasons()).containsExactly(Reason.OVER_CAPACITY);
		assertThat(check.capacities()).extracting(CapacityCheck::fits).containsExactly(true, false);
	}

	@Test
	void lotWithoutAChipTypeIsOnlyCheckedAgainstRulesWithoutOne() {
		weekTwoWithRules();

		ScheduleCheckView check = schedule.check(2027, 2, null, 600, null, ProcessType.A);

		assertThat(check.canSchedule()).isTrue();
		assertThat(check.capacities()).containsExactly(new CapacityCheck(null, null, 1000, 400, 1000, true));
	}

	@Test
	void weekWithoutRulesHasNoLimit() {
		weeks.createYear(2027);

		ScheduleCheckView check = schedule.check(2027, 2, null, 100_000, ChipType.Z_SERIES, ProcessType.A);

		assertThat(check.canSchedule()).isTrue();
		assertThat(check.capacities()).isEmpty();
	}

	@Test
	void closedWeekCantBeScheduledAndAllReasonsAreGiven() {
		weekTwoWithRules();
		weeks.changeStatus(2027, 2, WeekStatus.CLOSED);

		assertThat(schedule.check(2027, 2, null, 1, ChipType.H_SERIES, null).reasons())
				.containsExactly(Reason.WEEK_CLOSED);
		assertThat(schedule.check(2027, 2, null, 200, ChipType.Z_SERIES, null).reasons())
				.containsExactly(Reason.WEEK_CLOSED, Reason.OVER_CAPACITY);
	}

	@Test
	void storedLotIsCheckedWithItsDetailsAndInactiveLotsCantBeScheduled() {
		weekTwoWithRules();
		Lot stored = lot(LocalDate.of(2027, 3, 1), 50, ChipType.Z_SERIES, ProcessType.C);

		ScheduleCheckView check = schedule.check(2027, 2, stored.getLotId(), null, null, null);
		assertThat(check.canSchedule()).isTrue();
		assertThat(check.lot()).isEqualTo(
				new LotCandidate(stored.getLotId(), 50, ChipType.Z_SERIES, ProcessType.C, false));

		stored.changeStatus(LotStatus.CANCELED, NOW);
		lots.flush();
		assertThat(schedule.check(2027, 2, stored.getLotId(), null, null, null).reasons())
				.containsExactly(Reason.LOT_NOT_ACTIVE);
	}

	@Test
	void givenDetailsOverrideTheStoredLot() {
		weekTwoWithRules();
		Lot stored = lot(LocalDate.of(2027, 3, 1), 50, ChipType.Z_SERIES, ProcessType.C);

		ScheduleCheckView check = schedule.check(2027, 2, stored.getLotId(), 150, ChipType.H_SERIES, null);

		assertThat(check.lot()).isEqualTo(
				new LotCandidate(stored.getLotId(), 150, ChipType.H_SERIES, ProcessType.C, false));
		assertThat(check.capacities()).containsExactly(new CapacityCheck(null, null, 1000, 400, 550, true));
	}

	@Test
	void lotAlreadyScheduledInTheWeekIsNotCountedTwice() {
		weekTwoWithRules();
		// With the 400 already there, this lot alone makes Z_SERIES exactly 500.
		Lot stored = lot(LocalDate.of(2027, 1, 2), 100, ChipType.Z_SERIES, ProcessType.A);

		ScheduleCheckView check = schedule.check(2027, 2, stored.getLotId(), null, null, null);

		assertThat(check.canSchedule()).isTrue();
		assertThat(check.lot().alreadyScheduled()).isTrue();
		assertThat(check.capacities()).containsExactly(
				new CapacityCheck(null, null, 1000, 400, 500, true),
				new CapacityCheck(ChipType.Z_SERIES, null, 500, 400, 500, true));
		// Not scheduled in week 3, so nothing is taken off there.
		assertThat(schedule.check(2027, 3, stored.getLotId(), null, null, null).lot().alreadyScheduled()).isFalse();
	}

	@Test
	void unknownWeekOrLotIsNotFoundAndAWaferCountIsRequired() {
		weekTwoWithRules();
		Lot noWafers = lot(LocalDate.of(2027, 1, 6), null, ChipType.Z_SERIES, ProcessType.A);

		assertStatus(() -> schedule.check(2027, 60, null, 1, null, null), HttpStatus.NOT_FOUND,
				"Unknown production week: 2027-W60");
		assertStatus(() -> schedule.check(2027, 2, "nope", null, null, null), HttpStatus.NOT_FOUND, "Unknown lot: nope");
		assertStatus(() -> schedule.check(2027, 2, null, null, ChipType.Z_SERIES, null), HttpStatus.BAD_REQUEST,
				"waferCount is required");
		assertStatus(() -> schedule.check(2027, 2, noWafers.getLotId(), null, null, null), HttpStatus.BAD_REQUEST,
				"waferCount is required");
		assertStatus(() -> schedule.check(2027, 2, null, -1, null, null), HttpStatus.BAD_REQUEST,
				"waferCount can't be negative");
	}

	// --- the week's lots

	@Test
	void weekListsItsActiveLotsByShipDateThenId() {
		weeks.createYear(2027);
		Lot friday = lot(LocalDate.of(2027, 1, 8), 20, ChipType.H_SERIES, ProcessType.B);
		Lot saturday = lot(LocalDate.of(2027, 1, 2), 10, ChipType.Z_SERIES, ProcessType.A);
		Lot held = lot(LocalDate.of(2027, 1, 2), 30, ChipType.Z_SERIES, ProcessType.A);
		held.placeOnHold(NOW);
		lot(LocalDate.of(2027, 1, 1), 1, ChipType.Z_SERIES, ProcessType.A); // week 1's Friday
		lot(LocalDate.of(2027, 1, 9), 1, ChipType.Z_SERIES, ProcessType.A); // week 3's Saturday
		lot(null, 1, ChipType.Z_SERIES, ProcessType.A);
		lot(LocalDate.of(2027, 1, 5), 1, ChipType.Z_SERIES, ProcessType.A).changeStatus(LotStatus.CANCELED, NOW);
		lot(LocalDate.of(2027, 1, 5), 1, ChipType.Z_SERIES, ProcessType.A).changeStatus(LotStatus.COMPLETE, NOW);
		lots.flush();

		List<LotView> inWeek = schedule.lots(2027, 2);

		assertThat(inWeek).extracting(LotView::lotId)
				.containsExactly(saturday.getLotId(), held.getLotId(), friday.getLotId());
		assertThat(inWeek.get(1).onHold()).isTrue();
		assertThat(inWeek.get(2)).extracting(LotView::contractShipDate, LotView::waferCount, LotView::chipType,
				LotView::processType).containsExactly(LocalDate.of(2027, 1, 8), 20, ChipType.H_SERIES, ProcessType.B);
		assertThat(schedule.lots(2027, 4)).isEmpty();
		assertStatus(() -> schedule.lots(2027, 60), HttpStatus.NOT_FOUND, "Unknown production week: 2027-W60");
	}

	private static void assertStatus(Runnable call, HttpStatus status, String reason) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
			assertThat(e.getStatusCode()).isEqualTo(status);
			assertThat(e.getReason()).isEqualTo(reason);
		});
	}

	private Lot lot(LocalDate shipDate, Integer wafers, ChipType chipType, ProcessType processType) {
		Lot lot = Lot.registered("L" + ++lotIds);
		lot.changeDetails(new LotDetails(shipDate, wafers, chipType, processType), NOW);
		return lots.save(lot);
	}
}
