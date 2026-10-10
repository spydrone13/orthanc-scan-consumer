package com.spydrone.orthanc_scan_consumer.scan;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScanController.class)
class ScanControllerTest {

	private static final ScanEntity SCAN = ScanEntity.from(
			new ScanRecord("abc", "u", "S1", "L1", "S2", "W1", ScanType.TRANSITIONAL, "n", null, null), Instant.now());

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ScanRepository repository;

	@Test
	void listsScans() throws Exception {
		given(repository.findAllByOrderByReceivedAtDesc()).willReturn(List.of(SCAN));

		mvc.perform(get("/api/scans"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].clientId").value("abc"))
				.andExpect(jsonPath("$[0].scanType").value("transitional"))
				.andExpect(jsonPath("$[0].receivedAt").exists());
	}

	@Test
	void getsScanById() throws Exception {
		given(repository.findById("abc")).willReturn(Optional.of(SCAN));

		mvc.perform(get("/api/scans/abc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lotId").value("L1"))
				.andExpect(jsonPath("$.destinationStage").value("S2"))
				.andExpect(jsonPath("$.destinationWipLocation").value("W1"));
	}

	@Test
	void unknownScanIsNotFound() throws Exception {
		given(repository.findById("nope")).willReturn(Optional.empty());

		mvc.perform(get("/api/scans/nope")).andExpect(status().isNotFound());
	}
}
