package com.spydrone.orthanc_scan_consumer.lot.api;

import java.time.LocalDate;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotDetails;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

/** Body of PUT /api/lots/{lotId}/details. Fields left out are cleared. */
public record LotDetailsUpdate(LocalDate contractShipDate, Integer waferCount, ChipType chipType,
		ProcessType processType) {

	LotDetails toDetails() {
		return new LotDetails(contractShipDate, waferCount, chipType, processType);
	}
}
