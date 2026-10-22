package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.Instant;

/** Domain event: the lot's details changed. */
public record LotDetailsChanged(String lotId, LotDetails from, LotDetails to, Instant at) {
}
