package com.spydrone.orthanc_scan_consumer.scan;

/**
 * Message published by orthanc-scan-producer to the scans queue. A scan may set a destination
 * stage, a destination WIP location, or both (a WIP location in the next stage).
 *
 * @param correctionReason optional reason the operator gave with locationConfirmed; absent from older producers
 * @param locationConfirmed set when the operator confirmed the lot is at currentStage although the
 *        records had it elsewhere; absent from older producers, which confirmed with a reason alone
 */
public record ScanRecord(
		String clientId,
		String userName,
		String currentStage,
		String lotId,
		String destinationStage,
		String destinationWipLocation,
		ScanType scanType,
		String note,
		String correctionReason,
		Boolean locationConfirmed) {
}
