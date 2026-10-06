package com.spydrone.orthanc_scan_consumer.lot.application;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.lot.domain.Scan;
import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;

/** Command: apply a scan to its lot, creating the lot on its first scan. */
public record ApplyScan(String lotId, Scan scan, Instant at) {

	public static ApplyScan from(ScanRecord record, Instant at) {
		return new ApplyScan(record.lotId(), new Scan(record.clientId(), record.userName(), record.currentStage(),
				record.destinationStage(), record.destinationWipLocation(), record.note()), at);
	}
}
