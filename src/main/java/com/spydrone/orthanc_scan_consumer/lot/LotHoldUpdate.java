package com.spydrone.orthanc_scan_consumer.lot;

/** Body of PUT /api/lots/{lotId}/hold. */
public record LotHoldUpdate(Boolean onHold) {
}
