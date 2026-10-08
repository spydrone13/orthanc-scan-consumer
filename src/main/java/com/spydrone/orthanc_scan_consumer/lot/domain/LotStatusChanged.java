package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.Instant;

/** Domain event: the lot's status changed. */
public record LotStatusChanged(String lotId, LotStatus from, LotStatus to, Instant at) {
}
