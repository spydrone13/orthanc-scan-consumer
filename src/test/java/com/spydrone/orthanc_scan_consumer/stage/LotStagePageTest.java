package com.spydrone.orthanc_scan_consumer.stage;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LotStageController.class)
@Import(LotStagePageConfig.class)
class LotStagePageTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LotStageService service;

	@Test
	void friendlyUrlForwardsToPage() throws Exception {
		mvc.perform(get("/lot-stages"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/lot-stages.html"));
	}

	@Test
	void servesPageAndAssets() throws Exception {
		mvc.perform(get("/lot-stages.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"rows\"")));
		mvc.perform(get("/js/lot-stages.js")).andExpect(status().isOk());
		mvc.perform(get("/css/lot-stages.css")).andExpect(status().isOk());
	}
}
