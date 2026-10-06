package com.spydrone.orthanc_scan_consumer.lot;

import com.fasterxml.jackson.annotation.JsonProperty;

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
