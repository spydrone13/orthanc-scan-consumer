package com.spydrone.orthanc_scan_consumer.scan.deadletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeadLetterController.class)
class DeadLetterControllerTest {

	private static final DeadLetter ABC = deadLetter("abc");
	private static final DeadLetter XYZ = deadLetter("xyz");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DeadLetterService service;

	@Test
	void listsDeadLetters() throws Exception {
		given(service.list()).willReturn(new DeadLetterService.DeadLetters(3, List.of(ABC)));

		mvc.perform(get("/api/dead-letters"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.deadLetters[0].id").value("abc"))
				.andExpect(jsonPath("$.deadLetters[0].lotId").value("L1"))
				.andExpect(jsonPath("$.deadLetters[0].reason").value("boom"))
				.andExpect(jsonPath("$.deadLetters[0].failedAt").value("2026-10-08T13:00:00Z"));
	}

	@Test
	void retriesOneById() throws Exception {
		given(service.retry(any())).willReturn(1);

		mvc.perform(post("/api/dead-letters/abc/retry")).andExpect(status().isNoContent());

		assertThat(selection(true)).accepts(ABC).rejects(XYZ);
	}

	@Test
	void retryingAnUnknownIdIsNotFound() throws Exception {
		given(service.retry(any())).willReturn(0);

		mvc.perform(post("/api/dead-letters/nope/retry"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No dead-lettered scan nope"));
	}

	@Test
	void retriesAll() throws Exception {
		given(service.retry(any())).willReturn(2);

		mvc.perform(post("/api/dead-letters/retry"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.replayed").value(2));

		assertThat(selection(true)).accepts(ABC, XYZ);
	}

	@Test
	void discardsOneById() throws Exception {
		given(service.discard(any())).willReturn(1);

		mvc.perform(delete("/api/dead-letters/abc")).andExpect(status().isNoContent());

		assertThat(selection(false)).accepts(ABC).rejects(XYZ);
	}

	@Test
	void discardingAnUnknownIdIsNotFound() throws Exception {
		given(service.discard(any())).willReturn(0);

		mvc.perform(delete("/api/dead-letters/nope")).andExpect(status().isNotFound());
	}

	@SuppressWarnings("unchecked")
	private Predicate<DeadLetter> selection(boolean retry) {
		ArgumentCaptor<Predicate<DeadLetter>> captor = ArgumentCaptor.forClass(Predicate.class);
		if (retry) {
			verify(service).retry(captor.capture());
		}
		else {
			verify(service).discard(captor.capture());
		}
		return captor.getValue();
	}

	private static DeadLetter deadLetter(String clientId) {
		return new DeadLetter(clientId, clientId, "L1", "u", "boom", null, Instant.parse("2026-10-08T13:00:00Z"),
				"{\"clientId\":\"" + clientId + "\"}");
	}
}
