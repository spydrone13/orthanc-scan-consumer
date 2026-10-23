package com.spydrone.orthanc_scan_consumer.stage.adapter.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.spydrone.orthanc_scan_consumer.stage.application.StageSeedSource;
import com.spydrone.orthanc_scan_consumer.stage.domain.Stage;
import com.spydrone.orthanc_scan_consumer.stage.domain.WipLocation;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Seed stages from classpath:lot-stages.json, keyed by id in process order (same shape as the API). */
@Component
public class ClasspathJsonStageSeedSource implements StageSeedSource {

	private static final String SEED_RESOURCE = "lot-stages.json";

	@Override
	public List<Stage> load() {
		Map<String, SeedStage> seed;
		try (InputStream in = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
			seed = new JsonMapper().readValue(in, new TypeReference<LinkedHashMap<String, SeedStage>>() {});
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not read " + SEED_RESOURCE, e);
		}
		List<Stage> stages = new ArrayList<>();
		seed.forEach((id, stage) -> {
			Map<String, WipLocation> wip = new LinkedHashMap<>();
			stage.wipLocations().forEach((wipId, location) ->
					wip.put(wipId, new WipLocation(location == null ? null : location.description())));
			stages.add(new Stage(id, stage.description(), stages.size(), stage.nextStages(), wip,
					stage.nextWipLocations()));
		});
		return stages;
	}

	record SeedStage(
			String description,
			@JsonProperty("next-stages") List<String> nextStages,
			@JsonProperty("wip-locations") Map<String, SeedWipLocation> wipLocations,
			@JsonProperty("next-wip-locations") Map<String, List<String>> nextWipLocations) {
	}

	record SeedWipLocation(String description) {
	}
}
