package com.spydrone.orthanc_scan_consumer.lot.application;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.lot.domain.Discrepancy;
import com.spydrone.orthanc_scan_consumer.lot.history.LotStageEvent;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

/** Read model for a row of a lot's history, as returned by /api/lots/{lotId}/events. */
public record LotStageEventView(
		String clientId,
		String lotId,
		ScanType scanType,
		String userName,
		String fromStage,
		String fromWipLocation,
		String toStage,
		String toWipLocation,
		String note,
		Instant occurredAt,
		String rejectedReason,
		Discrepancy exception,
		String correctsClientId) {

	public static LotStageEventView of(LotStageEvent row) {
		return new LotStageEventView(row.getClientId(), row.getLotId(), row.getScanType(), row.getUserName(),
				row.getFromStage(), row.getFromWipLocation(), row.getToStage(), row.getToWipLocation(), row.getNote(),
				row.getOccurredAt(), row.getRejectedReason(), row.getException(), row.getCorrectsClientId());
	}
}
