package com.spydrone.orthanc_scan_consumer.week;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

/** The service against a real database. */
@DataJpaTest
@Import(ProductionWeekService.class)
class ProductionWeekServiceTest {

	@Autowired
	private ProductionWeekService service;

	@Autowired
	private ProductionWeekRepository repository;

	@Test
	void createsEveryWeekOfTheYearOpen() {
		List<ProductionWeekView> weeks = service.createYear(2026);

		assertThat(weeks).hasSize(52);
		assertThat(weeks).extracting(ProductionWeekView::weekNumber).startsWith(1, 2).endsWith(52);
		assertThat(weeks).allMatch(week -> week.status() == WeekStatus.OPEN && week.year() == 2026
				&& week.startDate().getDayOfWeek() == DayOfWeek.SATURDAY);
		assertThat(weeks.get(0)).isEqualTo(new ProductionWeekView(2026, 1, LocalDate.of(2025, 12, 27),
				LocalDate.of(2026, 1, 2), WeekStatus.OPEN));
	}

	@Test
	void creatingAYearAgainAddsNothingAndKeepsClosedWeeksClosed() {
		service.createYear(2026);
		service.changeStatus(2026, 7, WeekStatus.CLOSED);

		List<ProductionWeekView> weeks = service.createYear(2026);

		assertThat(weeks).hasSize(52);
		assertThat(repository.count()).isEqualTo(52);
		assertThat(weeks.get(6).status()).isEqualTo(WeekStatus.CLOSED);
	}

	@Test
	void creatingAYearFillsInMissingWeeks() {
		repository.save(new ProductionWeek(2026, 3, LocalDate.of(2026, 1, 16)));

		assertThat(service.createYear(2026)).hasSize(52);
	}

	@Test
	void yearsOutOfRangeAreBadRequests() {
		assertThatThrownBy(() -> service.createYear(1999)).isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		assertThatThrownBy(() -> service.createYear(2101)).isInstanceOf(ResponseStatusException.class);
		assertThat(repository.count()).isZero();
	}

	@Test
	void changesStatus() {
		service.createYear(2026);

		assertThat(service.changeStatus(2026, 7, WeekStatus.CLOSED).status()).isEqualTo(WeekStatus.CLOSED);
		assertThat(service.get(2026, 7).status()).isEqualTo(WeekStatus.CLOSED);
		assertThat(service.changeStatus(2026, 7, WeekStatus.OPEN).status()).isEqualTo(WeekStatus.OPEN);
	}

	@Test
	void unknownWeekIsNotFound() {
		service.createYear(2026);

		assertThatThrownBy(() -> service.get(2026, 53)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
			assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(e.getReason()).isEqualTo("Unknown production week: 2026-W53");
		});
		assertThatThrownBy(() -> service.changeStatus(2025, 1, WeekStatus.CLOSED))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void capacitiesAreSavedTotalFirstThenByChipThenByProcessThenBoth() {
		service.createYear(2026);

		ProductionWeekView week = service.setCapacities(2026, 7, List.of(
				new CapacityEntry(ChipType.Z_SERIES, ProcessType.A, 100),
				new CapacityEntry(null, ProcessType.B, 300),
				new CapacityEntry(ChipType.Z_SERIES, null, 500),
				new CapacityEntry(ChipType.H_SERIES, null, 200),
				new CapacityEntry(null, null, 1200)));

		List<CapacityEntry> expected = List.of(
				new CapacityEntry(null, null, 1200),
				new CapacityEntry(ChipType.Z_SERIES, null, 500),
				new CapacityEntry(ChipType.H_SERIES, null, 200),
				new CapacityEntry(null, ProcessType.B, 300),
				new CapacityEntry(ChipType.Z_SERIES, ProcessType.A, 100));
		assertThat(week.capacities()).isEqualTo(expected);
		assertThat(service.get(2026, 7).capacities()).isEqualTo(expected);
		assertThat(service.list(2026).get(6).capacities()).isEqualTo(expected);
		assertThat(service.get(2026, 8).capacities()).isEmpty();
	}

	@Test
	void capacitiesAreReplacedAndCanBeCleared() {
		service.createYear(2026);
		service.setCapacities(2026, 7, List.of(new CapacityEntry(null, null, 1200)));

		service.setCapacities(2026, 7, List.of(new CapacityEntry(ChipType.Q_SERIES, null, 50)));
		assertThat(service.get(2026, 7).capacities()).containsExactly(new CapacityEntry(ChipType.Q_SERIES, null, 50));

		service.setCapacities(2026, 7, List.of());
		assertThat(service.get(2026, 7).capacities()).isEmpty();
	}

	@Test
	void closedWeeksCanHaveCapacityAndCreatingTheYearAgainKeepsIt() {
		service.createYear(2026);
		service.changeStatus(2026, 7, WeekStatus.CLOSED);

		service.setCapacities(2026, 7, List.of(new CapacityEntry(null, null, 1200)));
		service.createYear(2026);

		assertThat(service.get(2026, 7).capacities()).containsExactly(new CapacityEntry(null, null, 1200));
	}

	@Test
	void invalidCapacitiesAreBadRequestsAndChangeNothing() {
		service.createYear(2026);
		service.setCapacities(2026, 7, List.of(new CapacityEntry(null, null, 1200)));

		assertBadRequest(() -> service.setCapacities(2026, 7, List.of(new CapacityEntry(null, null, null))),
				"waferCount is required");
		assertBadRequest(() -> service.setCapacities(2026, 7, List.of(new CapacityEntry(null, ProcessType.A, -1))),
				"waferCount can't be negative");
		assertBadRequest(() -> service.setCapacities(2026, 7, List.of(new CapacityEntry(ChipType.Z_SERIES, null, 5),
				new CapacityEntry(ChipType.Z_SERIES, null, 6))), "Duplicate capacity for Z_SERIES / any process");
		assertThat(service.get(2026, 7).capacities()).containsExactly(new CapacityEntry(null, null, 1200));
		assertThatThrownBy(() -> service.setCapacities(2026, 53, List.of()))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	private static void assertBadRequest(Runnable call, String reason) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
			assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(e.getReason()).isEqualTo(reason);
		});
	}

	@Test
	void listsOneYearOrAllInDateOrder() {
		service.createYear(2027);
		service.createYear(2026);

		assertThat(service.list(2027)).hasSize(53);
		List<ProductionWeekView> all = service.list(null);
		assertThat(all).hasSize(105);
		assertThat(all.get(0).endDate()).isEqualTo(LocalDate.of(2026, 1, 2));
		assertThat(all.get(52).endDate()).isEqualTo(LocalDate.of(2027, 1, 1));
	}
}
