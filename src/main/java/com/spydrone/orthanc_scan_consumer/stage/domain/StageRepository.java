package com.spydrone.orthanc_scan_consumer.stage.domain;

import java.util.List;

/** Port for storing stages; implemented by the persistence adapter. */
public interface StageRepository {

	List<Stage> findAllInProcessOrder();

	boolean isEmpty();

	/** Adds the stage, or replaces the stored stage with the same id. */
	void save(Stage stage);

	void saveAll(List<Stage> stages);
}
