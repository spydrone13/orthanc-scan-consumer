package com.spydrone.orthanc_scan_consumer.week;

import java.util.List;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

/**
 * Whether a lot can be scheduled into a production week, as returned by
 * /api/production-weeks/{year}/{week}/schedule-check.
 *
 * @param reasons why it can't; empty when it can
 * @param capacities the week's capacity rules that apply to the lot, in the week's rule order
 */
public record ScheduleCheckView(int year, int weekNumber, boolean canSchedule, List<Reason> reasons, LotCandidate lot,
		List<CapacityCheck> capacities) {

	public enum Reason {
		WEEK_CLOSED,
		OVER_CAPACITY,
		LOT_NOT_ACTIVE
	}

	/**
	 * The lot checked: stored details, overridden by any given.
	 *
	 * @param lotId null when only details were given
	 * @param alreadyScheduled the lot is active and its ship date is in the week, so its own wafers are already
	 *        in {@link CapacityCheck#scheduled()} and were taken off before adding them again
	 */
	public record LotCandidate(String lotId, int waferCount, ChipType chipType, ProcessType processType,
			boolean alreadyScheduled) {
	}

	/** A capacity rule (null type = any): what's scheduled without the lot, and with it. */
	public record CapacityCheck(ChipType chipType, ProcessType processType, int capacity, long scheduled,
			long afterScheduling, boolean fits) {
	}
}
