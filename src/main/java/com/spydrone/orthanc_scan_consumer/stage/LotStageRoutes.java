package com.spydrone.orthanc_scan_consumer.stage;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.spydrone.orthanc_scan_consumer.lot.domain.StageRoutes;

/** The lot rules' view of the stage graph: next stages and the WIP locations allowed in each. */
@Component
public class LotStageRoutes implements StageRoutes {

	private final LotStageService lotStageService;

	public LotStageRoutes(LotStageService lotStageService) {
		this.lotStageService = lotStageService;
	}

	@Override
	public boolean allows(String fromStage, String toStage, String toWipLocation) {
		Map<String, LotStage> stages = lotStageService.getStages();
		LotStage from = stages.get(fromStage);
		LotStage to = stages.get(toStage);
		if (from == null || to == null || !from.nextStages().contains(toStage)) {
			return false;
		}
		if (toWipLocation == null) {
			return true;
		}
		List<String> allowed = from.nextWipLocations().get(toStage);
		return to.wipLocations().containsKey(toWipLocation) && (allowed == null || allowed.contains(toWipLocation));
	}
}
