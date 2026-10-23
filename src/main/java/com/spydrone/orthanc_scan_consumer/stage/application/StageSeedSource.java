package com.spydrone.orthanc_scan_consumer.stage.application;

import java.util.List;

import com.spydrone.orthanc_scan_consumer.stage.domain.Stage;

/** Port for the stages to start with when none are stored. */
public interface StageSeedSource {

	/** The seed stages, in process order. */
	List<Stage> load();
}
