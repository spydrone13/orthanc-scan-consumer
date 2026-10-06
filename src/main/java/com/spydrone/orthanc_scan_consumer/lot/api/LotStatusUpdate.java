package com.spydrone.orthanc_scan_consumer.lot.api;

import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;

/** Body of PUT /api/lots/{lotId}/status. */
public record LotStatusUpdate(LotStatus status) {
}
