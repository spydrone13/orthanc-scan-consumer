package com.spydrone.orthanc_scan_consumer.lot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

@WebMvcTest(LotController.class)
class LotControllerTest {

	private static final ScanRecord SCAN =
			new ScanRecord("abc", "u", "S1", "L1", null, "WIP-1", ScanType.INFORMATIONAL, "n");
	private static final LotEntity LOT = new LotEntity("L1");
	private static final LotStageEvent EVENT;

	static {
		LOT.moveTo(SCAN.currentStage(), SCAN.destinationWipLocation(), Instant.now());
		EVENT = LotStageEvent.of(SCAN, SCAN.scanType(), null, null, LOT, Instant.now());
	}

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LotRepository lotRepository;

	@MockitoBean
	private LotStageEventRepository eventRepository;

	@MockitoBean
	private LotService lotService;

	@Test
	void listsLots() throws Exception {
		given(lotRepository.findAllByOrderByUpdatedAtDesc()).willReturn(List.of(LOT));

		mvc.perform(get("/api/lots"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].lotId").value("L1"))
				.andExpect(jsonPath("$[0].currentStage").value("S1"))
				.andExpect(jsonPath("$[0].wipLocation").value("WIP-1"))
				.andExpect(jsonPath("$[0].status").value("active"));
	}

	@Test
	void updatesStatus() throws Exception {
		LotEntity destroyed = new LotEntity("L1");
		destroyed.setStatus(LotStatus.DESTROYED, Instant.now());
		given(lotService.updateStatus(eq("L1"), eq(LotStatus.DESTROYED), any())).willReturn(destroyed);

		mvc.perform(put("/api/lots/L1/status").contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"destroyed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lotId").value("L1"))
				.andExpect(jsonPath("$.status").value("destroyed"));
	}

	@Test
	void unknownStatusValueIsBadRequest() throws Exception {
		mvc.perform(put("/api/lots/L1/status").contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"bogus\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void statusOfUnknownLotIsNotFound() throws Exception {
		given(lotService.updateStatus(eq("nope"), any(), any()))
				.willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown lot: nope"));

		mvc.perform(put("/api/lots/nope/status").contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"complete\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getsLotById() throws Exception {
		given(lotRepository.findById("L1")).willReturn(Optional.of(LOT));

		mvc.perform(get("/api/lots/L1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStage").value("S1"));
	}

	@Test
	void unknownLotIsNotFound() throws Exception {
		given(lotRepository.findById("nope")).willReturn(Optional.empty());

		mvc.perform(get("/api/lots/nope")).andExpect(status().isNotFound());
	}

	@Test
	void listsLotEvents() throws Exception {
		given(lotRepository.existsById("L1")).willReturn(true);
		given(eventRepository.findByLotIdOrderByOccurredAtDesc("L1")).willReturn(List.of(EVENT));

		mvc.perform(get("/api/lots/L1/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].clientId").value("abc"))
				.andExpect(jsonPath("$[0].scanType").value("informational"))
				.andExpect(jsonPath("$[0].toWipLocation").value("WIP-1"));
	}

	@Test
	void eventsForUnknownLotAreNotFound() throws Exception {
		given(lotRepository.existsById("nope")).willReturn(false);

		mvc.perform(get("/api/lots/nope/events")).andExpect(status().isNotFound());
	}
}
