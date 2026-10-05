package com.spydrone.orthanc_scan_consumer.stage;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(LotStageController.class)
class LotStageControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LotStageService service;

	@Test
	void returnsLotStagesAsKebabCaseJsonInOrder() throws Exception {
		Map<String, LotStage> stages = new LinkedHashMap<>();
		Map<String, WipLocation> intakeWip = new LinkedHashMap<>();
		intakeWip.put("INTAKE-002", new WipLocation("Intake 2"));
		intakeWip.put("INTAKE-001", new WipLocation("Intake 1"));
		stages.put("intake", new LotStage("Intake", List.of("wafer-prep"), intakeWip));
		stages.put("wafer-prep", new LotStage("Wafer Prep", List.of(), Map.of()));
		given(service.getStages()).willReturn(stages);

		String body = mvc.perform(get("/api/lot-stages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.intake['next-stages']", contains("wafer-prep")))
				.andExpect(jsonPath("$.intake['wip-locations']['INTAKE-001'].description").value("Intake 1"))
				.andExpect(jsonPath("$['wafer-prep']['wip-locations']").isEmpty())
				.andExpect(jsonPath("$['wafer-prep']['next-stages']", empty()))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).startsWith("{\"intake\":{\"description\":\"Intake\",\"next-stages\"");
		assertThat(body).contains("\"wip-locations\":{\"INTAKE-002\":{\"description\":\"Intake 2\"},\"INTAKE-001\"");
	}

	@Test
	void updatesStage() throws Exception {
		Map<String, WipLocation> wip = Map.of("INTAKE-009", new WipLocation("Overflow"));
		given(service.update("intake", List.of("wafer-prep"), wip))
				.willReturn(new LotStage("Intake", List.of("wafer-prep"), wip));

		mvc.perform(put("/api/lot-stages/intake").contentType(MediaType.APPLICATION_JSON)
						.content("{\"next-stages\":[\"wafer-prep\"],"
								+ "\"wip-locations\":{\"INTAKE-009\":{\"description\":\"Overflow\"}}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Intake"))
				.andExpect(jsonPath("$['wip-locations']['INTAKE-009'].description").value("Overflow"));
	}

	@Test
	void validationErrorIsProblemDetail() throws Exception {
		given(service.update(any(), any(), any()))
				.willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown next stage: nope"));

		mvc.perform(put("/api/lot-stages/intake").contentType(MediaType.APPLICATION_JSON)
						.content("{\"next-stages\":[\"nope\"],\"wip-locations\":{}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Unknown next stage: nope"));
	}
}
