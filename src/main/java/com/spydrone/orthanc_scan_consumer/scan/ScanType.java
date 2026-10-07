package com.spydrone.orthanc_scan_consumer.scan;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ScanType {
	@JsonProperty("transitional")
	TRANSITIONAL,
	@JsonProperty("informational")
	INFORMATIONAL,
	/** Only in lot history: the records were corrected to where a scan found the lot. */
	@JsonProperty("correction")
	CORRECTION
}
