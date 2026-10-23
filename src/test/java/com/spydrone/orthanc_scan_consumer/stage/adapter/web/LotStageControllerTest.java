package com.spydrone.orthanc_scan_consumer.stage.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.spydrone.orthanc_scan_consumer.stage.application.StageNotFoundException;
import com.spydrone.orthanc_scan_consumer.stage.application.StageService;
import com.spydrone.orthanc_scan_consumer.stage.application.UpdateStageRoutes;
import com.spydrone.orthanc_scan_consumer.stage.domain.InvalidStageChangeException;
import com.spydrone.orthanc_scan_consumer.stage.domain.Stage;
import com.spydrone.orthanc_scan_consumer.stage.domain.StageGraph;
import com.spydrone.orthanc_scan_consumer.stage.domain.WipLocation;

@WebMvcTest(LotStageController.class)
class LotStageControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private StageService service;

	@Test
	void returnsLotStagesAsKebabCaseJsonInOrder() throws Exception {
		Map<String, WipLocation> intakeWip = new LinkedHashMap<>();
		intakeWip.put("INTAKE-002", new WipLocation("Intake 2"));
		intakeWip.put("INTAKE-001", new WipLocation(null));
		given(service.stages()).willReturn(new StageGraph(List.of(
				new Stage("intake", "Intake", 0, List.of("wafer-prep"), intakeWip, Map.of()),
				new Stage("wafer-prep", "Wafer Prep", 1, List.of(), Map.of(), Map.of()))));

		String body = mvc.perform(get("/api/lot-stages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.intake['next-stages']", contains("wafer-prep")))
				.andExpect(jsonPath("$.intake['wip-locations']['INTAKE-001'].description").value("Intake 1"))
				.andExpect(jsonPath("$['wafer-prep']['wip-locations']").isEmpty())
				.andExpect(jsonPath("$['wafer-prep']['next-stages']", empty()))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).startsWith("{\"intake\":{\"description\":\"Intake\",\"next-stages\"");
		assertThat(body).contains("\"wip-locations\":{\"INTAKE-002\":{\"description\":\"Intake 2\"},\"INTAKE-001\"");
		assertThat(body).doesNotContain("next-wip-locations");
	}

	@Test
	void returnsAllowedNextStageWipLocationsTheNextStageStillHas() throws Exception {
		given(service.stages()).willReturn(new StageGraph(List.of(
				new Stage("intake", "Intake", 0, List.of("wafer-prep"), Map.of(),
						Map.of("wafer-prep", List.of("WAFER-PREP-001", "WAFER-PREP-009"))),
				new Stage("wafer-prep", "Wafer Prep", 1, List.of(),
						Map.of("WAFER-PREP-001", new WipLocation(null)), Map.of()))));

		mvc.perform(get("/api/lot-stages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.intake['next-wip-locations']['wafer-prep']", contains("WAFER-PREP-001")));
	}

	@Test
	void updatesAllowedNextStageWipLocations() throws Exception {
		Map<String, List<String>> nextWip = Map.of("wafer-prep", List.of("WAFER-PREP-001"));
		given(service.updateRoutes(new UpdateStageRoutes("intake", List.of("wafer-prep"), Map.of(), nextWip)))
				.willReturn(new Stage("intake", "Intake", 0, List.of("wafer-prep"), Map.of(), nextWip));

		mvc.perform(put("/api/lot-stages/intake").contentType(MediaType.APPLICATION_JSON)
						.content("{\"next-stages\":[\"wafer-prep\"],\"wip-locations\":{},"
								+ "\"next-wip-locations\":{\"wafer-prep\":[\"WAFER-PREP-001\"]}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$['next-wip-locations']['wafer-prep']", contains("WAFER-PREP-001")));
	}

	@Test
	void updatesStage() throws Exception {
		Map<String, WipLocation> wip = Map.of("INTAKE-009", new WipLocation("Overflow"));
		given(service.updateRoutes(new UpdateStageRoutes("intake", List.of("wafer-prep"), wip, null)))
				.willReturn(new Stage("intake", "Intake", 0, List.of("wafer-prep"), wip, Map.of()));

		mvc.perform(put("/api/lot-stages/intake").contentType(MediaType.APPLICATION_JSON)
						.content("{\"next-stages\":[\"wafer-prep\"],"
								+ "\"wip-locations\":{\"INTAKE-009\":{\"description\":\"Overflow\"}}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Intake"))
				.andExpect(jsonPath("$['wip-locations']['INTAKE-009'].description").value("Overflow"));
	}

	@Test
	void validationErrorIsProblemDetail() throws Exception {
		given(service.updateRoutes(any())).willThrow(new InvalidStageChangeException("Unknown next stage: nope"));

		mvc.perform(put("/api/lot-stages/intake").contentType(MediaType.APPLICATION_JSON)
						.content("{\"next-stages\":[\"nope\"],\"wip-locations\":{}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Unknown next stage: nope"));
	}

	@Test
	void unknownStageIsNotFoundProblemDetail() throws Exception {
		given(service.updateRoutes(any())).willThrow(new StageNotFoundException("nope"));

		mvc.perform(put("/api/lot-stages/nope").contentType(MediaType.APPLICATION_JSON)
						.content("{\"next-stages\":[],\"wip-locations\":{}}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Unknown stage: nope"));
	}
}
