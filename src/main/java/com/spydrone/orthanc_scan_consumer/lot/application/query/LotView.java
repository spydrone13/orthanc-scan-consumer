package com.spydrone.orthanc_scan_consumer.lot.application.query;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;

/**
 * Read model for a lot, as returned by /api/lots.
 *
 * @param lastScan the scan that put the lot where it is; null if not known, and in the responses to
 *        status and hold changes
 */
public record LotView(
		String lotId,
		String currentStage,
		String wipLocation,
		LotStatus status,
		boolean onHold,
		Instant updatedAt,
		LastScan lastScan) {

	/** Who scanned the lot to where it is, and when; the producer quotes it when the location looks wrong. */
	public record LastScan(String clientId, String userName, Instant at) {
	}

	public LotView(String lotId, String currentStage, String wipLocation, LotStatus status, boolean onHold,
			Instant updatedAt) {
		this(lotId, currentStage, wipLocation, status, onHold, updatedAt, null);
	}

	/** For the JPQL projection: the stored status name, and the last scan's columns for the nested record. */
	public LotView(String lotId, String currentStage, String wipLocation, String status, boolean onHold,
			Instant updatedAt, String lastScanClientId, String lastScanUserName, Instant lastScanAt) {
		this(lotId, currentStage, wipLocation, LotStatus.valueOf(status), onHold, updatedAt,
				lastScanClientId == null ? null : new LastScan(lastScanClientId, lastScanUserName, lastScanAt));
	}

	public static LotView of(Lot lot) {
		return new LotView(lot.getLotId(), lot.location().stage(), lot.location().wipLocation(), lot.getStatus(),
				lot.isOnHold(), lot.getUpdatedAt());
	}
}
