package com.spydrone.orthanc_scan_consumer.lot.application.query;

import java.time.Instant;
import java.time.LocalDate;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotDetails;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

/**
 * Read model for a lot, as returned by /api/lots. contractShipDate, waferCount, chipType and processType
 * are the lot's {@link LotDetails}, each null when not given.
 *
 * @param lastScan the scan that put the lot where it is; null if not known, and in the responses to
 *        status, hold and details changes
 */
public record LotView(
		String lotId,
		String currentStage,
		String wipLocation,
		LotStatus status,
		boolean onHold,
		LocalDate contractShipDate,
		Integer waferCount,
		ChipType chipType,
		ProcessType processType,
		Instant updatedAt,
		LastScan lastScan) {

	/** Who scanned the lot to where it is, and when; the producer quotes it when the location looks wrong. */
	public record LastScan(String clientId, String userName, Instant at) {
	}

	public LotView(String lotId, String currentStage, String wipLocation, LotStatus status, boolean onHold,
			Instant updatedAt) {
		this(lotId, currentStage, wipLocation, status, onHold, null, null, null, null, updatedAt, null);
	}

	/**
	 * For the JPQL projection: the stored status, chip type and process type names, and the last scan's
	 * columns for the nested record.
	 */
	public LotView(String lotId, String currentStage, String wipLocation, String status, boolean onHold,
			LocalDate contractShipDate, Integer waferCount, String chipType, String processType, Instant updatedAt,
			String lastScanClientId, String lastScanUserName, Instant lastScanAt) {
		this(lotId, currentStage, wipLocation, LotStatus.valueOf(status), onHold, contractShipDate, waferCount,
				chipType == null ? null : ChipType.valueOf(chipType),
				processType == null ? null : ProcessType.valueOf(processType), updatedAt,
				lastScanClientId == null ? null : new LastScan(lastScanClientId, lastScanUserName, lastScanAt));
	}

	public static LotView of(Lot lot) {
		LotDetails details = lot.details();
		return new LotView(lot.getLotId(), lot.location().stage(), lot.location().wipLocation(), lot.getStatus(),
				lot.isOnHold(), details.contractShipDate(), details.waferCount(), details.chipType(),
				details.processType(), lot.getUpdatedAt(), null);
	}
}
