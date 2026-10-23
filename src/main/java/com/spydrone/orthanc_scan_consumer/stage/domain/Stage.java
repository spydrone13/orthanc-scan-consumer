package com.spydrone.orthanc_scan_consumer.stage.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregate root for a stage: its next stages, its WIP locations (keyed by id, in display order) and,
 * for a next stage listed in {@link #nextWipLocations()}, the only WIP locations in it a lot may be
 * scanned to from this stage (empty: the stage itself only). A next stage not listed allows any of
 * its WIP locations. Only the routes change, through {@link #changeRoutes}.
 */
public class Stage {

	private final String id;
	private final String description;
	/** Process order, from the order of stages in the seed. */
	private final int position;
	private List<String> nextStages;
	private Map<String, WipLocation> wipLocations;
	private Map<String, List<String>> nextWipLocations;

	public Stage(String id, String description, int position, List<String> nextStages,
			Map<String, WipLocation> wipLocations, Map<String, List<String>> nextWipLocations) {
		this.id = id;
		this.description = description;
		this.position = position;
		this.nextStages = List.copyOf(nextStages);
		this.wipLocations = Collections.unmodifiableMap(new LinkedHashMap<>(wipLocations));
		this.nextWipLocations = nextWipLocations == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(nextWipLocations));
	}

	public String id() {
		return id;
	}

	public String description() {
		return description;
	}

	public int position() {
		return position;
	}

	public List<String> nextStages() {
		return nextStages;
	}

	/** WIP locations as stored: a description is null until set. */
	public Map<String, WipLocation> wipLocations() {
		return wipLocations;
	}

	/** WIP locations with the default description for those that have none. */
	public Map<String, WipLocation> describedWipLocations() {
		Map<String, WipLocation> described = new LinkedHashMap<>();
		wipLocations.forEach((wipId, wip) -> described.put(wipId, new WipLocation(
				wip != null && wip.description() != null
						? wip.description()
						: WipLocation.defaultDescription(description, wipId))));
		return described;
	}

	public Map<String, List<String>> nextWipLocations() {
		return nextWipLocations;
	}

	/**
	 * Replaces the next stages, WIP locations and the WIP locations allowed in each next stage
	 * ({@code null} or a missing next stage: any). Blank WIP ids are dropped and a blank description
	 * is kept as null so the default shows. Allowed WIP locations are kept in the next stage's
	 * display order.
	 *
	 * @param graph the stages, to check next stages and their WIP locations against
	 * @throws InvalidStageChangeException when the routes break a rule; nothing is changed
	 */
	public void changeRoutes(List<String> nextStages, Map<String, WipLocation> wipLocations,
			Map<String, List<String>> nextWipLocations, StageGraph graph) {
		List<String> next = nextStages == null ? List.of() : nextStages;
		for (String nextId : next) {
			if (id.equals(nextId)) {
				throw new InvalidStageChangeException("A stage cannot be its own next stage");
			}
			if (nextId == null || graph.find(nextId).isEmpty()) {
				throw new InvalidStageChangeException("Unknown next stage: " + nextId);
			}
		}
		requireNoDuplicates(next, "next stage");

		Map<String, WipLocation> wip = new LinkedHashMap<>();
		if (wipLocations != null) {
			wipLocations.forEach((wipId, location) -> {
				String trimmedId = blankToNull(wipId);
				if (trimmedId == null) {
					return;
				}
				if (wip.containsKey(trimmedId)) {
					throw new InvalidStageChangeException("Duplicate WIP location: " + trimmedId);
				}
				wip.put(trimmedId, new WipLocation(location == null ? null : blankToNull(location.description())));
			});
		}
		requireNoDuplicates(List.copyOf(wip.keySet()), "WIP location");

		Map<String, List<String>> nextWip = nextWipLocations == null ? Map.of() : nextWipLocations;
		for (String nextId : nextWip.keySet()) {
			if (!next.contains(nextId)) {
				throw new InvalidStageChangeException(nextId + " is not a next stage");
			}
		}
		Map<String, List<String>> allowed = new LinkedHashMap<>();
		for (String nextId : next) {
			List<String> wipIds = nextWip.get(nextId);
			if (wipIds == null) {
				continue;
			}
			Set<String> nextStageWip = graph.find(nextId).orElseThrow().wipLocations().keySet();
			for (String wipId : wipIds) {
				if (!nextStageWip.contains(wipId)) {
					throw new InvalidStageChangeException("Unknown WIP location " + wipId + " in stage " + nextId);
				}
			}
			requireNoDuplicates(wipIds, "WIP location for " + nextId);
			allowed.put(nextId, nextStageWip.stream().filter(wipIds::contains).toList());
		}

		this.nextStages = List.copyOf(next);
		this.wipLocations = Collections.unmodifiableMap(wip);
		this.nextWipLocations = Collections.unmodifiableMap(allowed);
	}

	private static void requireNoDuplicates(List<String> values, String what) {
		Set<String> seen = new HashSet<>();
		for (String value : values) {
			if (!seen.add(value.toLowerCase())) {
				throw new InvalidStageChangeException("Duplicate " + what + ": " + value);
			}
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
