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
