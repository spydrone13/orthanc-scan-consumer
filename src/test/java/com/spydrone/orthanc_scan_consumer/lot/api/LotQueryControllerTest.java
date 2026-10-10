package com.spydrone.orthanc_scan_consumer.lot.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.spydrone.orthanc_scan_consumer.lot.application.LotNotFoundException;
import com.spydrone.orthanc_scan_consumer.lot.application.query.LotQueries;
import com.spydrone.orthanc_scan_consumer.lot.application.query.LotStageEventView;
import com.spydrone.orthanc_scan_consumer.lot.application.query.LotView;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

/** Pins the read API's paths and JSON shapes, which the producer and UI depend on. */
@WebMvcTest(LotQueryController.class)
class LotQueryControllerTest {

	private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
	private static final LotView LOT = new LotView("L1", "S1", "WIP-1", LotStatus.ACTIVE, false, NOW);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LotQueries queries;

	@Test
	void listsLots() throws Exception {
		given(queries.list()).willReturn(List.of(LOT));

		mvc.perform(get("/api/lots"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].lotId").value("L1"))
				.andExpect(jsonPath("$[0].currentStage").value("S1"))
				.andExpect(jsonPath("$[0].wipLocation").value("WIP-1"))
				.andExpect(jsonPath("$[0].status").value("active"))
				.andExpect(jsonPath("$[0].onHold").value(false))
				.andExpect(jsonPath("$[0].updatedAt").value("2026-10-05T12:00:00Z"));
	}

	@Test
	void getsLotById() throws Exception {
		given(queries.get("L1")).willReturn(LOT);

		mvc.perform(get("/api/lots/L1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStage").value("S1"));
	}

	@Test
	void unknownLotIsNotFound() throws Exception {
		given(queries.get("nope")).willThrow(new LotNotFoundException("nope"));

		mvc.perform(get("/api/lots/nope")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Unknown lot: nope"));
	}

	@Test
	void listsLotEvents() throws Exception {
		given(queries.history("L1")).willReturn(List.of(new LotStageEventView("abc", "L1", ScanType.INFORMATIONAL,
				"u", null, null, "S1", "WIP-1", "n", NOW, null, null, null)));

		mvc.perform(get("/api/lots/L1/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].clientId").value("abc"))
				.andExpect(jsonPath("$[0].scanType").value("informational"))
				.andExpect(jsonPath("$[0].toWipLocation").value("WIP-1"))
				.andExpect(jsonPath("$[0].rejectedReason").isEmpty());
	}

	@Test
	void eventsForUnknownLotAreNotFound() throws Exception {
		given(queries.history("nope")).willThrow(new LotNotFoundException("nope"));

		mvc.perform(get("/api/lots/nope/events")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Unknown lot: nope"));
	}
}
