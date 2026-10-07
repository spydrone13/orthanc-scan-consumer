package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.scan.ScanType;

/**
 * Domain event: a scan moved the lot from one location to another.
 *
 * @param discrepancy why the scan was flagged for review; null if it wasn't
 */
public record ScanApplied(String lotId, Scan scan, ScanType scanType, Location from, Location to,
		Discrepancy discrepancy, Instant at) {
}
