package com.spydrone.orthanc_scan_consumer.stage.adapter.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spydrone.orthanc_scan_consumer.stage.application.StageService;
import com.spydrone.orthanc_scan_consumer.stage.application.UpdateStageRoutes;
import com.spydrone.orthanc_scan_consumer.stage.domain.Stage;
import com.spydrone.orthanc_scan_consumer.stage.domain.StageGraph;
import com.spydrone.orthanc_scan_consumer.stage.domain.WipLocation;

@RestController
@RequestMapping("/api/lot-stages")
public class LotStageController {

	private final StageService stageService;

	public LotStageController(StageService stageService) {
		this.stageService = stageService;
	}

	/** All stages keyed by id, in process order. */
	@GetMapping
	public Map<String, LotStageResponse> getLotStages() {
		StageGraph graph = stageService.stages();
		Map<String, LotStageResponse> stages = new LinkedHashMap<>();
		for (Stage stage : graph.stages()) {
			stages.put(stage.id(), LotStageResponse.of(stage, graph.allowedNextWipLocations(stage)));
		}
		return stages;
	}

	@PutMapping("/{id}")
	public LotStageResponse updateLotStage(@PathVariable String id, @RequestBody LotStageUpdate update) {
		Stage stage = stageService.updateRoutes(new UpdateStageRoutes(id, update.nextStages(),
				toDomain(update.wipLocations()), update.nextWipLocations()));
		return LotStageResponse.of(stage, stage.nextWipLocations());
	}

	/** Keeps the order and null entries; the domain decides what they mean. */
	private static Map<String, WipLocation> toDomain(Map<String, WipLocationDto> wipLocations) {
		if (wipLocations == null) {
			return null;
		}
		Map<String, WipLocation> wip = new LinkedHashMap<>();
		wipLocations.forEach((wipId, location) ->
				wip.put(wipId, location == null ? null : new WipLocation(location.description())));
		return wip;
	}
}
