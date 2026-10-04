package com.spydrone.orthanc_scan_consumer.stage;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LotStage(
		String description,
		@JsonProperty("next-stages") List<String> nextStages,
		@JsonProperty("wip-locations") List<String> wipLocations) {
}
