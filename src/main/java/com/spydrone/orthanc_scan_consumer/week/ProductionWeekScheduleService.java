package com.spydrone.orthanc_scan_consumer.week;

import static com.spydrone.orthanc_scan_consumer.week.ProductionWeekService.FIRST_YEAR;
import static com.spydrone.orthanc_scan_consumer.week.ProductionWeekService.LAST_YEAR;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.week.ScheduledWeekView.CapacityUsage;

/**
 * Wafers scheduled against production weeks. A lot is scheduled in the week (Saturday to Friday) containing
 * its contract ship date, and only active lots count; on hold or not.
 */
@Service
@Transactional(readOnly = true)
public class ProductionWeekScheduleService {

	private final ScheduledWafersRepository scheduled;
	private final ProductionWeekRepository weeks;

	public ProductionWeekScheduleService(ScheduledWafersRepository scheduled, ProductionWeekRepository weeks) {
		this.scheduled = scheduled;
		this.weeks = weeks;
	}

	/** Every week of the year that has scheduled lots or capacity rules, in week order. */
	public List<ScheduledWeekView> scheduled(int year) {
		if (year < FIRST_YEAR || year > LAST_YEAR) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Year must be between " + FIRST_YEAR + " and " + LAST_YEAR);
		}
		List<LocalDate> fridays = ProductionWeeks.fridaysOf(year);
		Map<LocalDate, Integer> weekByFriday = new HashMap<>();
		for (int i = 0; i < fridays.size(); i++) {
			weekByFriday.put(fridays.get(i), i + 1);
		}

		Map<Integer, List<ScheduledLots>> lotsByWeek = new HashMap<>();
		for (ScheduledLots group : scheduled.findActiveShippingBetween(fridays.get(0).minusDays(6),
				fridays.get(fridays.size() - 1))) {
			int week = weekByFriday.get(group.shipDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)));
			lotsByWeek.computeIfAbsent(week, w -> new ArrayList<>()).add(group);
		}
		Map<Integer, List<CapacityEntry>> rulesByWeek = new HashMap<>();
		for (ProductionWeek week : weeks.findByYearOrderByWeekNumber(year)) {
			if (!week.getCapacities().isEmpty()) {
				rulesByWeek.put(week.getWeekNumber(), week.getCapacities().stream().map(CapacityEntry::of).toList());
			}
		}

		List<ScheduledWeekView> views = new ArrayList<>();
		for (int week = 1; week <= fridays.size(); week++) {
			List<ScheduledLots> lots = lotsByWeek.getOrDefault(week, List.of());
			List<CapacityEntry> rules = rulesByWeek.getOrDefault(week, List.of());
			if (lots.isEmpty() && rules.isEmpty()) {
				continue;
			}
			views.add(new ScheduledWeekView(week, lots.stream().mapToLong(ScheduledLots::lots).sum(),
					lots.stream().mapToLong(ScheduledLots::wafers).sum(),
					rules.stream().map(rule -> new CapacityUsage(rule.chipType(), rule.processType(), rule.waferCount(),
							lots.stream().filter(l -> l.matches(rule)).mapToLong(ScheduledLots::wafers).sum()))
							.toList()));
		}
		return views;
	}
}
