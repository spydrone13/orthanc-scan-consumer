package com.spydrone.orthanc_scan_consumer.stage.adapter.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.spydrone.orthanc_scan_consumer.stage.domain.Stage;

/**
 * A stage as the API returns it; WIP locations are keyed by id, in display order.
 *
 * @param nextWipLocations for a next stage listed here, the only WIP locations in it a lot may be
 *        scanned to from this stage (empty: the stage itself only). A next stage not listed allows
 *        any of its WIP locations.
 */
public record LotStageResponse(
		String description,
		@JsonProperty("next-stages") List<String> nextStages,
		@JsonProperty("wip-locations") Map<String, WipLocationDto> wipLocations,
		@JsonProperty("next-wip-locations") @JsonInclude(JsonInclude.Include.NON_EMPTY)
		Map<String, List<String>> nextWipLocations) {

	/** WIP locations without a description get the default. */
	static LotStageResponse of(Stage stage, Map<String, List<String>> nextWipLocations) {
		Map<String, WipLocationDto> wip = new LinkedHashMap<>();
		stage.describedWipLocations().forEach((wipId, location) ->
				wip.put(wipId, new WipLocationDto(location.description())));
		return new LotStageResponse(stage.description(), stage.nextStages(), wip, nextWipLocations);
	}
}
