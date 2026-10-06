package com.spydrone.orthanc_scan_consumer.lot.domain;

/**
 * A scan as the lot sees it.
 *
 * @param scanStage the stage the operator scanned from
 */
public record Scan(
		String clientId,
		String userName,
		String scanStage,
		String destinationStage,
		String destinationWipLocation,
		String note) {
}
