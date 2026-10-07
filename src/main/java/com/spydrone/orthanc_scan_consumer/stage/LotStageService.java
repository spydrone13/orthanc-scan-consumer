package com.spydrone.orthanc_scan_consumer.stage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The lot-stage graph, stored in the database. Seeded from classpath:lot-stages.json when the
 * table is empty; after that, only next stages and WIP locations (a stage's own, and those allowed
 * in each next stage) change, through {@link #update}.
 */
@Service
public class LotStageService {

	private static final Logger log = LoggerFactory.getLogger(LotStageService.class);
	private static final String SEED_RESOURCE = "lot-stages.json";

	private final LotStageRepository repository;

	public LotStageService(LotStageRepository repository) {
		this.repository = repository;
		if (repository.count() == 0) {
			seed();
		}
	}

	private void seed() {
		Map<String, LotStage> stages;
		try (InputStream in = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
			stages = new JsonMapper().readValue(in, new TypeReference<LinkedHashMap<String, LotStage>>() {});
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not read " + SEED_RESOURCE, e);
		}
		List<LotStageEntity> entities = new ArrayList<>();
		stages.forEach((id, stage) -> entities.add(new LotStageEntity(id, stage, entities.size())));
		repository.saveAll(entities);
		log.info("Seeded {} lot stages from {}", entities.size(), SEED_RESOURCE);
	}

	/**
	 * All stages keyed by id, in process order. Allowed next-stage WIP locations that the next stage
	 * no longer has are left out.
	 */
	@Transactional(readOnly = true)
	public Map<String, LotStage> getStages() {
		Map<String, LotStage> stages = new LinkedHashMap<>();
		for (LotStageEntity entity : repository.findAllByOrderByPositionAsc()) {
			stages.put(entity.getId(), entity.toLotStage());
		}
		stages.replaceAll((id, stage) -> withoutRemovedNextWipLocations(stage, stages));
		return stages;
	}

	private static LotStage withoutRemovedNextWipLocations(LotStage stage, Map<String, LotStage> stages) {
		Map<String, List<String>> nextWip = new LinkedHashMap<>();
		stage.nextWipLocations().forEach((nextId, wipIds) -> {
			LotStage next = stages.get(nextId);
			Set<String> existing = next == null ? Set.of() : next.wipLocations().keySet();
			nextWip.put(nextId, wipIds.stream().filter(existing::contains).toList());
		});
		return new LotStage(stage.description(), stage.nextStages(), stage.wipLocations(), nextWip);
	}

	/**
	 * Replaces a stage's next stages, WIP locations and the WIP locations allowed in each next stage
	 * ({@code null} or a missing next stage: any). Allowed WIP locations come back in the next
	 * stage's display order.
	 */
	@Transactional
	public LotStage update(String id, List<String> nextStages, Map<String, WipLocation> wipLocations,
			Map<String, List<String>> nextWipLocations) {
		LotStageEntity entity = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stage: " + id));

		List<String> next = nextStages == null ? List.of() : nextStages;
		for (String nextId : next) {
			if (id.equals(nextId)) {
				throw badRequest("A stage cannot be its own next stage");
			}
			if (nextId == null || !repository.existsById(nextId)) {
				throw badRequest("Unknown next stage: " + nextId);
			}
		}
		requireNoDuplicates(next, "next stage");

		// Blank ids are dropped; a blank description is stored as null so the default shows.
		Map<String, WipLocation> wip = new LinkedHashMap<>();
		if (wipLocations != null) {
			wipLocations.forEach((wipId, location) -> {
				String trimmedId = blankToNull(wipId);
				if (trimmedId == null) {
					return;
				}
				if (wip.containsKey(trimmedId)) {
					throw badRequest("Duplicate WIP location: " + trimmedId);
				}
				wip.put(trimmedId, new WipLocation(location == null ? null : blankToNull(location.description())));
			});
		}
		requireNoDuplicates(List.copyOf(wip.keySet()), "WIP location");

		Map<String, List<String>> nextWip = nextWipLocations == null ? Map.of() : nextWipLocations;
		Map<String, List<String>> allowed = new LinkedHashMap<>();
		for (String nextId : nextWip.keySet()) {
			if (!next.contains(nextId)) {
				throw badRequest(nextId + " is not a next stage");
			}
		}
		for (String nextId : next) {
			List<String> wipIds = nextWip.get(nextId);
			if (wipIds == null) {
				continue;
			}
			Set<String> nextStageWip = repository.findById(nextId).orElseThrow().toLotStage().wipLocations().keySet();
			for (String wipId : wipIds) {
				if (!nextStageWip.contains(wipId)) {
					throw badRequest("Unknown WIP location " + wipId + " in stage " + nextId);
				}
			}
			requireNoDuplicates(wipIds, "WIP location for " + nextId);
			allowed.put(nextId, nextStageWip.stream().filter(wipIds::contains).toList());
		}

		entity.replace(next, wip, allowed);
		return entity.toLotStage();
	}

	private static void requireNoDuplicates(List<String> values, String what) {
		Set<String> seen = new HashSet<>();
		for (String value : values) {
			if (!seen.add(value.toLowerCase())) {
				throw badRequest("Duplicate " + what + ": " + value);
			}
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
