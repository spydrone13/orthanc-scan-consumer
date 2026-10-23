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

/**
 * Wafers scheduled against production weeks. A lot is scheduled in the week (Saturday to Friday) containing
 * its contract ship date, and only active lots count; on hold or not.
 */
@Service
@Transactional(readOnly = true)
public class ProductionWeekScheduleService {

	private final ScheduledWafersRepository scheduled;
	private final ProductionWeekRepository weeks;
	private final LotRepository lots;

	public ProductionWeekScheduleService(ScheduledWafersRepository scheduled, ProductionWeekRepository weeks,
			LotRepository lots) {
		this.scheduled = scheduled;
		this.weeks = weeks;
		this.lots = lots;
	}

	/**
	 * Whether a lot fits in the week: the week is open, the lot (if given by id) is active, and for every
	 * capacity rule that applies to it, what's scheduled plus its wafers is within capacity. A week with no
	 * applicable rules has no limit. Details given override the stored lot's.
	 */
	public ScheduleCheckView check(int year, int weekNumber, String lotId, Integer waferCount, ChipType chipType,
			ProcessType processType) {
		ProductionWeek week = weeks.findByYearAndWeekNumber(year, weekNumber)
				.orElseThrow(() -> ProductionWeekService.notFound(year, weekNumber));
		Lot lot = lotId == null ? null
				: lots.findById(lotId).orElseThrow(
						() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown lot: " + lotId));
		LotDetails stored = lot == null ? LotDetails.NONE : lot.details();
		Integer wafers = waferCount != null ? waferCount : stored.waferCount();
		ChipType chip = chipType != null ? chipType : stored.chipType();
		ProcessType process = processType != null ? processType : stored.processType();
		if (wafers == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "waferCount is required");
		}
		if (wafers < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "waferCount can't be negative");
		}

		boolean active = lot == null || lot.getStatus() == LotStatus.ACTIVE;
		LocalDate shipDate = stored.contractShipDate();
		boolean alreadyScheduled = lot != null && active && shipDate != null
				&& !shipDate.isBefore(week.getStartDate()) && !shipDate.isAfter(week.getEndDate());
		// The lot's own wafers as stored, which is what the schedule counted.
		long ownWafers = alreadyScheduled && stored.waferCount() != null ? stored.waferCount() : 0;
		ScheduledLots candidate = new ScheduledLots(null, chip, process, 1, wafers);
		ScheduledLots storedLot = new ScheduledLots(null, stored.chipType(), stored.processType(), 1, ownWafers);

		List<ScheduledLots> inWeek = scheduled.findActiveShippingBetween(week.getStartDate(), week.getEndDate());
		List<CapacityCheck> checks = week.getCapacities().stream()
				.map(CapacityEntry::of)
				.filter(candidate::matches)
				.map(rule -> {
					long before = inWeek.stream().filter(g -> g.matches(rule)).mapToLong(ScheduledLots::wafers).sum()
							- (storedLot.matches(rule) ? ownWafers : 0);
					long after = before + wafers;
					return new CapacityCheck(rule.chipType(), rule.processType(), rule.waferCount(), before, after,
							after <= rule.waferCount());
				})
				.toList();

		List<Reason> reasons = new ArrayList<>();
		if (week.getStatus() == WeekStatus.CLOSED) {
			reasons.add(Reason.WEEK_CLOSED);
		}
		if (checks.stream().anyMatch(c -> !c.fits())) {
			reasons.add(Reason.OVER_CAPACITY);
		}
		if (!active) {
			reasons.add(Reason.LOT_NOT_ACTIVE);
		}
		return new ScheduleCheckView(year, weekNumber, reasons.isEmpty(), reasons,
				new LotCandidate(lotId, wafers, chip, process, alreadyScheduled), checks);
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
