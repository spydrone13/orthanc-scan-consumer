package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.Instant;

/** Domain event: the lot was placed on hold ({@code onHold} true) or released from it. */
public record LotHoldChanged(String lotId, boolean onHold, Instant at) {
}
