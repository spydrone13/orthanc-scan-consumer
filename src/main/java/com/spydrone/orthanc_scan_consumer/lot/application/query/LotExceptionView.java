package com.spydrone.orthanc_scan_consumer.lot.application.query;

/**
 * Read model for a history row flagged for review, as returned by /api/lot-exceptions.
 *
 * @param corrects for a correction, the row of the scan that put the lot where the records wrongly
 *        had it; null otherwise or if it isn't known
 */
public record LotExceptionView(LotStageEventView event, LotStageEventView corrects) {
}
