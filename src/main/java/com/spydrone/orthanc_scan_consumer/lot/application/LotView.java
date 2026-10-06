package com.spydrone.orthanc_scan_consumer.lot.application;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;

/** Read model for a lot, as returned by /api/lots. */
public record LotView(
		String lotId,
		String currentStage,
		String wipLocation,
		LotStatus status,
		boolean onHold,
		Instant updatedAt) {

	public static LotView of(Lot lot) {
		return new LotView(lot.getLotId(), lot.location().stage(), lot.location().wipLocation(), lot.getStatus(),
				lot.isOnHold(), lot.getUpdatedAt());
	}
}
