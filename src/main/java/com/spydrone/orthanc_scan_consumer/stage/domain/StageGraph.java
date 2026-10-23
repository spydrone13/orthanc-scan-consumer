package com.spydrone.orthanc_scan_consumer.stage.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** All stages, in process order, and the routes a lot may take between them. */
public class StageGraph {

	private final Map<String, Stage> stages = new LinkedHashMap<>();

	public StageGraph(List<Stage> stagesInProcessOrder) {
		stagesInProcessOrder.forEach(stage -> stages.put(stage.id(), stage));
	}

	public List<Stage> stages() {
		return List.copyOf(stages.values());
	}

	public Optional<Stage> find(String id) {
		return Optional.ofNullable(stages.get(id));
	}

	/** A stage's allowed next-stage WIP locations, leaving out those the next stage no longer has. */
	public Map<String, List<String>> allowedNextWipLocations(Stage stage) {
		Map<String, List<String>> allowed = new LinkedHashMap<>();
		stage.nextWipLocations().forEach((nextId, wipIds) -> {
			Stage next = stages.get(nextId);
			Set<String> existing = next == null ? Set.of() : next.wipLocations().keySet();
			allowed.put(nextId, wipIds.stream().filter(existing::contains).toList());
		});
		return allowed;
	}

	/**
	 * Whether a lot at {@code fromStage} may go to {@code toStage}, and to {@code toWipLocation} there
	 * when it's set: toStage must be a next stage of fromStage, and the WIP location one of toStage's
	 * that fromStage allows.
	 */
	public boolean allows(String fromStage, String toStage, String toWipLocation) {
		Stage from = stages.get(fromStage);
		Stage to = stages.get(toStage);
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
