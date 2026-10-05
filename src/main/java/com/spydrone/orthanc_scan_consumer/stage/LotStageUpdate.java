package com.spydrone.orthanc_scan_consumer.stage;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body of PUT /api/lot-stages/{id}; same field names as GET. */
public record LotStageUpdate(
		@JsonProperty("next-stages") List<String> nextStages,
		@JsonProperty("wip-locations") List<String> wipLocations) {
}
