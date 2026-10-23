package com.spydrone.orthanc_scan_consumer.week;

import java.time.LocalDate;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

/** Active lots shipping on one date with one chip type and process type (either may be null), summed. */
public record ScheduledLots(LocalDate shipDate, ChipType chipType, ProcessType processType, long lots, long wafers) {

	/** For the JPQL projection: the stored type names. */
	public ScheduledLots(LocalDate shipDate, String chipType, String processType, Long lots, Long wafers) {
		this(shipDate, chipType == null ? null : ChipType.valueOf(chipType),
				processType == null ? null : ProcessType.valueOf(processType), lots, wafers);
	}

	/** Whether these lots count toward a capacity rule: each type the rule names must match. */
	boolean matches(CapacityEntry rule) {
		return (rule.chipType() == null || rule.chipType() == chipType)
				&& (rule.processType() == null || rule.processType() == processType);
	}
}
