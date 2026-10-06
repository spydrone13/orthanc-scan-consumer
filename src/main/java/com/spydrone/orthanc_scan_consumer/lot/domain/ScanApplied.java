package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.scan.ScanType;

/** Domain event: a scan moved the lot from one location to another. */
public record ScanApplied(String lotId, Scan scan, ScanType scanType, Location from, Location to, Instant at) {
}
