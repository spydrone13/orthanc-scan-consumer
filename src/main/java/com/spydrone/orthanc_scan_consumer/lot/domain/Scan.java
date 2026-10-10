package com.spydrone.orthanc_scan_consumer.lot.domain;

/**
 * A scan as the lot sees it.
 *
 * @param scanStage the stage the operator scanned from, where the lot physically is
 * @param correctionReason optional reason the operator gave with locationConfirmed
 * @param locationConfirmed set when the operator confirmed the lot is at scanStage although the
 *        records had it elsewhere
 */
public record Scan(
		String clientId,
		String userName,
		String scanStage,
		String destinationStage,
		String destinationWipLocation,
		String note,
		String correctionReason,
		Boolean locationConfirmed) {

	/** Whether the operator confirmed the lot is here; older producers confirmed with a reason alone. */
	public boolean confirmsLocation() {
		return Boolean.TRUE.equals(locationConfirmed) || (correctionReason != null && !correctionReason.isBlank());
	}
}
