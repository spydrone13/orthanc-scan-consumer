package com.spydrone.orthanc_scan_consumer.week;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProductionWeeksTest {

	@Test
	void yearStartingAfterFridayHas52Weeks() {
		List<LocalDate> fridays = ProductionWeeks.fridaysOf(2026);

		assertThat(fridays).hasSize(52);
		assertThat(fridays.get(0)).isEqualTo(LocalDate.of(2026, 1, 2));
		assertThat(fridays.get(51)).isEqualTo(LocalDate.of(2026, 12, 25));
	}

	@Test
	void yearStartingOnFridayHas53Weeks() {
		List<LocalDate> fridays = ProductionWeeks.fridaysOf(2027);

		assertThat(fridays).hasSize(53);
		assertThat(fridays.get(0)).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(fridays.get(52)).isEqualTo(LocalDate.of(2027, 12, 31));
	}

	@Test
	void everyWeekEndsOnAFridayAWeekAfterTheLast() {
		List<LocalDate> fridays = ProductionWeeks.fridaysOf(2028);

		assertThat(fridays).allMatch(date -> date.getDayOfWeek() == DayOfWeek.FRIDAY);
		for (int i = 1; i < fridays.size(); i++) {
			assertThat(fridays.get(i)).isEqualTo(fridays.get(i - 1).plusWeeks(1));
		}
	}
}
