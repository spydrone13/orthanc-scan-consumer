package com.spydrone.orthanc_scan_consumer.lot.domain;

/** Why a scan left a lot where it was. The names match the producer's error codes. */
public enum RejectionReason {
	LOT_ON_HOLD,
	LOT_CANCELED,
	LOT_DESTROYED,
	LOT_COMPLETE;

	static RejectionReason forStatus(LotStatus status) {
		return switch (status) {
			case CANCELED -> LOT_CANCELED;
			case DESTROYED -> LOT_DESTROYED;
			case COMPLETE -> LOT_COMPLETE;
			case ACTIVE -> throw new IllegalArgumentException("Active lots aren't rejected");
		};
	}
}
