package com.spydrone.orthanc_scan_consumer.lot;

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

import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

@WebMvcTest(LotController.class)
class LotControllerTest {

	private static final ScanRecord SCAN =
			new ScanRecord("abc", "u", "S1", "L1", "WIP-1", ScanType.INFORMATIONAL, "n");
	private static final LotEntity LOT = new LotEntity("L1");
	private static final LotStageEvent EVENT;

	static {
		LOT.apply(SCAN.scanType(), SCAN.currentStage(), SCAN.destination(), Instant.now());
		EVENT = LotStageEvent.of(SCAN, null, null, LOT, Instant.now());
	}

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LotRepository lotRepository;

	@MockitoBean
	private LotStageEventRepository eventRepository;

	@Test
	void listsLots() throws Exception {
		given(lotRepository.findAllByOrderByUpdatedAtDesc()).willReturn(List.of(LOT));

		mvc.perform(get("/api/lots"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].lotId").value("L1"))
				.andExpect(jsonPath("$[0].currentStage").value("S1"))
				.andExpect(jsonPath("$[0].wipLocation").value("WIP-1"));
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
