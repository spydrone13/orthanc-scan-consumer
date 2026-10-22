package com.spydrone.orthanc_scan_consumer.week;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProductionWeekService {

	static final int FIRST_YEAR = 2000;
	static final int LAST_YEAR = 2100;

	private final ProductionWeekRepository weeks;

	public ProductionWeekService(ProductionWeekRepository weeks) {
		this.weeks = weeks;
	}

	/**
	 * Creates the year's missing weeks, open. Weeks that already exist are left as they are, so this can be
	 * run again safely.
	 */
	public List<ProductionWeekView> createYear(int year) {
		if (year < FIRST_YEAR || year > LAST_YEAR) {
			throw badRequest("Year must be between " + FIRST_YEAR + " and " + LAST_YEAR);
		}
		Set<Integer> existing = weeks.findByYearOrderByWeekNumber(year).stream()
				.map(ProductionWeek::getWeekNumber)
				.collect(Collectors.toSet());
		List<LocalDate> fridays = ProductionWeeks.fridaysOf(year);
		for (int i = 0; i < fridays.size(); i++) {
			if (!existing.contains(i + 1)) {
				weeks.save(new ProductionWeek(year, i + 1, fridays.get(i)));
			}
		}
		return views(weeks.findByYearOrderByWeekNumber(year));
	}

	/** One year's weeks, or every week when year is null; in date order. */
	@Transactional(readOnly = true)
	public List<ProductionWeekView> list(Integer year) {
		return views(year == null ? weeks.findAllByOrderByEndDate() : weeks.findByYearOrderByWeekNumber(year));
	}

	@Transactional(readOnly = true)
	public ProductionWeekView get(int year, int week) {
		return ProductionWeekView.of(existing(year, week));
	}

	public ProductionWeekView changeStatus(int year, int week, WeekStatus status) {
		ProductionWeek productionWeek = existing(year, week);
		productionWeek.changeStatus(status);
		return ProductionWeekView.of(productionWeek);
	}

	/**
	 * Replaces the week's capacity rules, sorted total first. Each chip type / process type combination may
	 * appear once; an empty list clears them. Closed weeks can be changed too.
	 */
	public ProductionWeekView setCapacities(int year, int week, List<CapacityEntry> capacities) {
		ProductionWeek productionWeek = existing(year, week);
		Set<String> seen = new HashSet<>();
		for (CapacityEntry entry : capacities) {
			if (entry.waferCount() == null) {
				throw badRequest("waferCount is required");
			}
			if (entry.waferCount() < 0) {
				throw badRequest("waferCount can't be negative");
			}
			if (!seen.add(entry.label())) {
				throw badRequest("Duplicate capacity for " + entry.label());
			}
		}
		productionWeek.replaceCapacities(capacities.stream()
				.sorted(CapacityEntry.DISPLAY_ORDER)
				.map(entry -> new WeekCapacity(entry.chipType(), entry.processType(), entry.waferCount()))
				.toList());
		return ProductionWeekView.of(productionWeek);
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private ProductionWeek existing(int year, int week) {
		return weeks.findByYearAndWeekNumber(year, week).orElseThrow(() -> new ResponseStatusException(
				HttpStatus.NOT_FOUND, "Unknown production week: %d-W%02d".formatted(year, week)));
	}

	private static List<ProductionWeekView> views(List<ProductionWeek> weeks) {
		return weeks.stream().map(ProductionWeekView::of).toList();
	}
}
