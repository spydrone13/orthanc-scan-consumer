package com.spydrone.orthanc_scan_consumer.lot.application;

import java.time.Instant;

/** Command: place a lot on hold ({@code onHold = true}) or release it. */
public record SetLotHold(String lotId, boolean onHold, Instant at) {
}
