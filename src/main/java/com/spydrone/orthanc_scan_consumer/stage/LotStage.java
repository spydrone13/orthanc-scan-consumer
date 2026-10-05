package com.spydrone.orthanc_scan_consumer.stage;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A stage; WIP locations are keyed by id, in display order. */
public record LotStage(
		String description,
		@JsonProperty("next-stages") List<String> nextStages,
		@JsonProperty("wip-locations") Map<String, WipLocation> wipLocations) {
}
