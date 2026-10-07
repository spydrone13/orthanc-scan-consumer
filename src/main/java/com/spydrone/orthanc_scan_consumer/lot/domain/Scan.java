package com.spydrone.orthanc_scan_consumer.lot.domain;

/**
 * A scan as the lot sees it.
 *
 * @param scanStage the stage the operator scanned from, where the lot physically is
 * @param correctionReason set when the operator confirmed the lot is at scanStage although the
 *        records had it elsewhere
 */
public record Scan(
		String clientId,
		String userName,
		String scanStage,
		String destinationStage,
		String destinationWipLocation,
		String note,
		String correctionReason) {
}
