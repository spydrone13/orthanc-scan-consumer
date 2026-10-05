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
		stages.put("intake", new LotStage("Intake", List.of("wafer-prep"), List.of("INTAKE-001")));
		stages.put("wafer-prep", new LotStage("Wafer Prep", List.of(), List.of()));
		given(service.getStages()).willReturn(stages);

		String body = mvc.perform(get("/api/lot-stages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.intake['next-stages']", contains("wafer-prep")))
				.andExpect(jsonPath("$.intake['wip-locations']", contains("INTAKE-001")))
				.andExpect(jsonPath("$['wafer-prep']['next-stages']", empty()))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).startsWith("{\"intake\":{\"description\":\"Intake\",\"next-stages\"");
	}

	@Test
	void updatesStage() throws Exception {
		given(service.update("intake", List.of("wafer-prep"), List.of("INTAKE-009")))
				.willReturn(new LotStage("Intake", List.of("wafer-prep"), List.of("INTAKE-009")));

		mvc.perform(put("/api/lot-stages/intake").contentType(MediaType.APPLICATION_JSON)
						.content("{\"next-stages\":[\"wafer-prep\"],\"wip-locations\":[\"INTAKE-009\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Intake"))
				.andExpect(jsonPath("$['wip-locations']", contains("INTAKE-009")));
	}

	@Test
	void validationErrorIsProblemDetail() throws Exception {
		given(service.update(any(), any(), any()))
				.willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown next stage: nope"));

		mvc.perform(put("/api/lot-stages/intake").contentType(MediaType.APPLICATION_JSON)
						.content("{\"next-stages\":[\"nope\"],\"wip-locations\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Unknown next stage: nope"));
	}
}
