package com.spydrone.orthanc_scan_consumer.week;

import java.util.Comparator;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

/**
 * A week's capacity for a chip type and/or process type, in wafers, as sent to and returned by
 * /api/production-weeks. A null type means any; both null is the week's total.
 */
public record CapacityEntry(ChipType chipType, ProcessType processType, Integer waferCount) {

	/** Total first, then by chip type, then by process type, then by both; each in enum order. */
	static final Comparator<CapacityEntry> DISPLAY_ORDER = Comparator
			.comparingInt(CapacityEntry::specificity)
			.thenComparing(CapacityEntry::chipType, Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparing(CapacityEntry::processType, Comparator.nullsFirst(Comparator.naturalOrder()));

	static CapacityEntry of(WeekCapacity capacity) {
		return new CapacityEntry(capacity.getChipType(), capacity.getProcessType(), capacity.getWaferCount());
	}

	/** "Z_SERIES / any process", for error messages. */
	String label() {
		return (chipType == null ? "any chip" : chipType.name()) + " / "
				+ (processType == null ? "any process" : "process " + processType.name());
	}

	private int specificity() {
		if (chipType == null) {
			return processType == null ? 0 : 2;
		}
		return processType == null ? 1 : 3;
	}
}
