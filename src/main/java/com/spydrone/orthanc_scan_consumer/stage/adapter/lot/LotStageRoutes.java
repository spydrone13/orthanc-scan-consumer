package com.spydrone.orthanc_scan_consumer.stage.adapter.lot;

import org.springframework.stereotype.Component;

import com.spydrone.orthanc_scan_consumer.lot.domain.StageRoutes;
import com.spydrone.orthanc_scan_consumer.stage.application.StageService;

/** The lot rules' view of the stage graph: next stages and the WIP locations allowed in each. */
@Component
public class LotStageRoutes implements StageRoutes {

	private final StageService stageService;

	public LotStageRoutes(StageService stageService) {
		this.stageService = stageService;
	}

	@Override
	public boolean allows(String fromStage, String toStage, String toWipLocation) {
		return stageService.stages().allows(fromStage, toStage, toWipLocation);
	}
}
