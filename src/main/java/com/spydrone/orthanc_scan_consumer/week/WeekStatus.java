package com.spydrone.orthanc_scan_consumer.week;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Whether a production week is still open for planning. */
public enum WeekStatus {
	@JsonProperty("open")
	OPEN,
	@JsonProperty("closed")
	CLOSED
}
