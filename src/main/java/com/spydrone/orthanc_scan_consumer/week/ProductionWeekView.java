package com.spydrone.orthanc_scan_consumer.week;

import java.time.LocalDate;

/** A production week as returned by /api/production-weeks. */
public record ProductionWeekView(int year, int weekNumber, LocalDate startDate, LocalDate endDate, WeekStatus status) {

	public static ProductionWeekView of(ProductionWeek week) {
		return new ProductionWeekView(week.getYear(), week.getWeekNumber(), week.getStartDate(), week.getEndDate(),
				week.getStatus());
	}
}
