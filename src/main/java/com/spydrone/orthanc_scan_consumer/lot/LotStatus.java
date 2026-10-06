package com.spydrone.orthanc_scan_consumer.lot;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Only active lots can be scanned; scans for the others are rejected as LOT_CANCELED etc. */
public enum LotStatus {
	@JsonProperty("active")
	ACTIVE,
	@JsonProperty("canceled")
	CANCELED,
	@JsonProperty("destroyed")
	DESTROYED,
	@JsonProperty("complete")
	COMPLETE
}
