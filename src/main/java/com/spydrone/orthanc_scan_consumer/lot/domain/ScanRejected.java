package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.scan.ScanType;

/** Domain event: a scan was refused and the lot stayed where it was. */
public record ScanRejected(String lotId, Scan scan, ScanType scanType, Location location,
		RejectionReason reason, Instant at) {
}
