package com.spydrone.orthanc_scan_consumer.scan.deadletter;

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

@WebMvcTest(DeadLetterController.class)
@Import(DeadLetterPageConfig.class)
class DeadLetterPageTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DeadLetterService service;

	@Test
	void friendlyUrlForwardsToPage() throws Exception {
		mvc.perform(get("/dead-letters"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/dead-letters.html"));
	}

	@Test
	void servesPageAndAssets() throws Exception {
		mvc.perform(get("/dead-letters.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"rows\"")));
		mvc.perform(get("/js/dead-letters.js")).andExpect(status().isOk());
	}
}
