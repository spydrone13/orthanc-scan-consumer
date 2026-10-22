package com.spydrone.orthanc_scan_consumer.week;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A row of production_week_capacities: how many wafers the week can take for a chip type and/or process
 * type. A null type means any. The types are kept as plain strings like {@link ProductionWeek}'s status.
 */
@Embeddable
public class WeekCapacity {

	private String chipType;
	private String processType;
	@Column(nullable = false)
	private int waferCount;

	protected WeekCapacity() {
	}

	public WeekCapacity(ChipType chipType, ProcessType processType, int waferCount) {
		this.chipType = chipType == null ? null : chipType.name();
		this.processType = processType == null ? null : processType.name();
		this.waferCount = waferCount;
	}

	public ChipType getChipType() {
		return chipType == null ? null : ChipType.valueOf(chipType);
	}

	public ProcessType getProcessType() {
		return processType == null ? null : ProcessType.valueOf(processType);
	}

	public int getWaferCount() {
		return waferCount;
	}
}
