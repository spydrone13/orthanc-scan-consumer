package com.spydrone.orthanc_scan_consumer.lot;

/** Body of PUT /api/lots/{lotId}/status. */
public record LotStatusUpdate(LotStatus status) {
}
