package com.spydrone.orthanc_scan_consumer.stage;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A stage; WIP locations are keyed by id, in display order.
 *
 * @param nextWipLocations for a next stage listed here, the only WIP locations in it a lot may be
 *        scanned to from this stage (empty: the stage itself only). A next stage not listed allows
 *        any of its WIP locations.
 */
public record LotStage(
		String description,
		@JsonProperty("next-stages") List<String> nextStages,
		@JsonProperty("wip-locations") Map<String, WipLocation> wipLocations,
		@JsonProperty("next-wip-locations") @JsonInclude(JsonInclude.Include.NON_EMPTY)
		Map<String, List<String>> nextWipLocations) {

	public LotStage {
		if (nextWipLocations == null) {
			nextWipLocations = Map.of();
		}
	}

	/** A stage that allows any WIP location in each of its next stages. */
	public LotStage(String description, List<String> nextStages, Map<String, WipLocation> wipLocations) {
		this(description, nextStages, wipLocations, Map.of());
	}
}
