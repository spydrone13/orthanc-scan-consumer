package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.Instant;

/**
 * Domain event: the records had the lot at another stage than the one it was scanned at, so it was
 * moved there before the scan was applied.
 *
 * @param correctsClientId the scan that put the lot where the records had it; null if not known
 */
public record LocationCorrected(String lotId, Scan scan, Location from, Location to, Discrepancy discrepancy,
		String correctsClientId, Instant at) {
}
