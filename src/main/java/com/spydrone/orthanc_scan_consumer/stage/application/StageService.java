package com.spydrone.orthanc_scan_consumer.stage.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spydrone.orthanc_scan_consumer.stage.domain.Stage;
import com.spydrone.orthanc_scan_consumer.stage.domain.StageGraph;
import com.spydrone.orthanc_scan_consumer.stage.domain.StageRepository;

/**
 * Use cases for the stage graph. Seeded from the {@link StageSeedSource} when no stages are stored;
 * after that, only a stage's routes change, through {@link #updateRoutes}.
 */
@Service
public class StageService {

	private static final Logger log = LoggerFactory.getLogger(StageService.class);

	private final StageRepository repository;

	public StageService(StageRepository repository, StageSeedSource seedSource) {
		this.repository = repository;
		if (repository.isEmpty()) {
			List<Stage> stages = seedSource.load();
			repository.saveAll(stages);
			log.info("Seeded {} lot stages", stages.size());
		}
	}

	/** All stages, in process order. */
	@Transactional(readOnly = true)
	public StageGraph stages() {
		return new StageGraph(repository.findAllInProcessOrder());
	}

	/**
	 * Replaces a stage's routes; see {@link Stage#changeRoutes}.
	 *
	 * @throws StageNotFoundException when there is no such stage
	 */
	@Transactional
	public Stage updateRoutes(UpdateStageRoutes command) {
		StageGraph graph = stages();
		Stage stage = graph.find(command.stageId()).orElseThrow(() -> new StageNotFoundException(command.stageId()));
		stage.changeRoutes(command.nextStages(), command.wipLocations(), command.nextWipLocations(), graph);
		repository.save(stage);
		return stage;
	}
}
