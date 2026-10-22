package com.spydrone.orthanc_scan_consumer.lot.application.command;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.lot.domain.LotDetails;

/** Command: replace a lot's details, registering the lot if it hasn't been scanned yet. */
public record SetLotDetails(String lotId, LotDetails details, Instant at) {
}
