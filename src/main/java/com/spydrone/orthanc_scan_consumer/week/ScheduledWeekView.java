package com.spydrone.orthanc_scan_consumer.week;

import java.util.List;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

/**
 * What's scheduled in a production week, as returned by /api/production-weeks/scheduled: active lots whose
 * contract ship date falls in the week.
 *
 * @param lots all of them, including those with no wafer count
 * @param scheduledWafers all of their wafers
 * @param capacities each of the week's capacity rules with the wafers that count toward it, in the rules' order
 */
public record ScheduledWeekView(int weekNumber, long lots, long scheduledWafers, List<CapacityUsage> capacities) {

	/** A capacity rule (null type = any) and the scheduled wafers of the lots it matches. */
	public record CapacityUsage(ChipType chipType, ProcessType processType, int capacity, long scheduled) {
	}
}
