package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.LocalDate;

/** What the lot is, as opposed to where it is. Any of these may be unknown (null). */
public record LotDetails(LocalDate contractShipDate, Integer waferCount, ChipType chipType, ProcessType processType) {

	public static final LotDetails NONE = new LotDetails(null, null, null, null);
}
