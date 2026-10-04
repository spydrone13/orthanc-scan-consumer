package com.spydrone.orthanc_scan_consumer.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LotStageController.class)
@Import(LotStageService.class)
class LotStageControllerTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void returnsLotStages() throws Exception {
		String body = mvc.perform(get("/api/lot-stages"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", aMapWithSize(14)))
				.andExpect(jsonPath("$.intake.description").value("Intake"))
				.andExpect(jsonPath("$.photolithography['next-stages']",
						contains("wet-etching", "dry-etching", "deposition")))
				.andExpect(jsonPath("$.shipped['next-stages']", empty()))
				.andExpect(jsonPath("$['wafer-prep']['wip-locations'][0]").value("WAFER-PREP-001"))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).startsWith("{\"intake\":{\"description\":\"Intake\",\"next-stages\"");
	}
}
