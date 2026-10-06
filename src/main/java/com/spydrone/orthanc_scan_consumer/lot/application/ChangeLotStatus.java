package com.spydrone.orthanc_scan_consumer.lot.application;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;

/** Command: set a lot's status. */
public record ChangeLotStatus(String lotId, LotStatus status, Instant at) {
}
