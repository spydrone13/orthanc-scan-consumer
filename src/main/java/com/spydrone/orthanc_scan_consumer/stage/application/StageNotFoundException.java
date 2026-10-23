package com.spydrone.orthanc_scan_consumer.stage.application;

/** No stage exists with the given id. */
public class StageNotFoundException extends RuntimeException {

	private final String stageId;

	public StageNotFoundException(String stageId) {
		super("Unknown stage: " + stageId);
		this.stageId = stageId;
	}

	public String getStageId() {
		return stageId;
	}
}
