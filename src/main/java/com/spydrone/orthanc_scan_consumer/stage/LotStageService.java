package com.spydrone.orthanc_scan_consumer.stage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Lot-stage graph loaded once from classpath:lot-stages.json, in file order. */
@Service
public class LotStageService {

	private static final String RESOURCE = "lot-stages.json";

	private final Map<String, LotStage> stages;

	public LotStageService(JsonMapper jsonMapper) {
		try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
			LinkedHashMap<String, LotStage> loaded =
					jsonMapper.readValue(in, new TypeReference<LinkedHashMap<String, LotStage>>() {});
			this.stages = Collections.unmodifiableMap(loaded);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not read " + RESOURCE, e);
		}
	}

	public Map<String, LotStage> getStages() {
		return stages;
	}
}
