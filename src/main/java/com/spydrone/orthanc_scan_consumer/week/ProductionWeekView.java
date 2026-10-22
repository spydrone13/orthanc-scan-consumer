package com.spydrone.orthanc_scan_consumer.week;

import java.time.LocalDate;
import java.util.List;

/**
 * A production week as returned by /api/production-weeks.
 *
 * @param capacities the week's capacity rules, total first (see {@link CapacityEntry#DISPLAY_ORDER})
 */
public record ProductionWeekView(int year, int weekNumber, LocalDate startDate, LocalDate endDate, WeekStatus status,
		List<CapacityEntry> capacities) {

	public ProductionWeekView(int year, int weekNumber, LocalDate startDate, LocalDate endDate, WeekStatus status) {
		this(year, weekNumber, startDate, endDate, status, List.of());
	}

	/** Reads the capacities, so call it inside the transaction that loaded the week. */
	public static ProductionWeekView of(ProductionWeek week) {
		return new ProductionWeekView(week.getYear(), week.getWeekNumber(), week.getStartDate(), week.getEndDate(),
				week.getStatus(), week.getCapacities().stream().map(CapacityEntry::of).toList());
	}
}
