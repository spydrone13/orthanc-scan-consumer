package com.spydrone.orthanc_scan_consumer.stage.adapter.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body of PUT /api/lot-stages/{id}; same field names as GET. */
public record LotStageUpdate(
		@JsonProperty("next-stages") List<String> nextStages,
		@JsonProperty("wip-locations") Map<String, WipLocationDto> wipLocations,
		@JsonProperty("next-wip-locations") Map<String, List<String>> nextWipLocations) {
}
