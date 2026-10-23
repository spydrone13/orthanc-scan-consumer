package com.spydrone.orthanc_scan_consumer.week;

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

@WebMvcTest(ProductionWeekController.class)
@Import(ProductionWeekPageConfig.class)
class ProductionWeekPageTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ProductionWeekService service;

	@MockitoBean
	private ProductionWeekScheduleService schedule;

	@Test
	void friendlyUrlForwardsToPage() throws Exception {
		mvc.perform(get("/production-weeks").param("year", "2026"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/production-weeks.html"));
	}

	@Test
	void servesPageAndAssets() throws Exception {
		mvc.perform(get("/production-weeks.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"rows\"")))
				.andExpect(content().string(containsString("id=\"seed\"")))
				.andExpect(content().string(containsString("col-capacity")));
		mvc.perform(get("/js/production-weeks.js")).andExpect(status().isOk());
		mvc.perform(get("/css/lot-stages.css"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(".week-badge")));
	}
}
