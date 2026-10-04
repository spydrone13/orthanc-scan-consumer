package com.spydrone.orthanc_scan_consumer.scan;

/** Message published by orthanc-scan-producer to the scans queue. */
public record ScanRecord(
		String clientId,
		String userName,
		String currentStage,
		String lotId,
		String destination,
		ScanType scanType,
		String note) {
}
