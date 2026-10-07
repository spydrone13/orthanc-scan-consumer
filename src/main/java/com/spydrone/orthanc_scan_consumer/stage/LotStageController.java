package com.spydrone.orthanc_scan_consumer.stage;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lot-stages")
public class LotStageController {

	private final LotStageService lotStageService;

	public LotStageController(LotStageService lotStageService) {
		this.lotStageService = lotStageService;
	}

	@GetMapping
	public Map<String, LotStage> getLotStages() {
		return lotStageService.getStages();
	}

	@PutMapping("/{id}")
	public LotStage updateLotStage(@PathVariable String id, @RequestBody LotStageUpdate update) {
		return lotStageService.update(id, update.nextStages(), update.wipLocations(), update.nextWipLocations());
	}
}
