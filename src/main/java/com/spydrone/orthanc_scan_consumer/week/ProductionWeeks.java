package com.spydrone.orthanc_scan_consumer.week;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** The production calendar: week N of a year is the week ending on that year's Nth Friday. */
public final class ProductionWeeks {

	private ProductionWeeks() {
	}

	/** Every Friday in the year, in order: 52 or 53 of them. Index 0 ends week 1. */
	public static List<LocalDate> fridaysOf(int year) {
		List<LocalDate> fridays = new ArrayList<>();
		for (LocalDate friday = LocalDate.of(year, 1, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.FRIDAY));
				friday.getYear() == year; friday = friday.plusWeeks(1)) {
			fridays.add(friday);
		}
		return fridays;
	}
}
