package com.spydrone.orthanc_scan_consumer.stage.application;

import java.util.List;
import java.util.Map;

import com.spydrone.orthanc_scan_consumer.stage.domain.WipLocation;

/** Replace a stage's next stages, WIP locations and allowed next-stage WIP locations. */
public record UpdateStageRoutes(
		String stageId,
		List<String> nextStages,
		Map<String, WipLocation> wipLocations,
		Map<String, List<String>> nextWipLocations) {
}
