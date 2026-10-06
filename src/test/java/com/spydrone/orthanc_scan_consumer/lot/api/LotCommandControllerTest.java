package com.spydrone.orthanc_scan_consumer.lot.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.lot.application.ChangeLotStatus;
import com.spydrone.orthanc_scan_consumer.lot.application.LotCommandHandler;
import com.spydrone.orthanc_scan_consumer.lot.application.SetLotHold;
import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;

/** Pins the write API's paths, bodies and responses. */
@WebMvcTest(LotCommandController.class)
class LotCommandControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private LotCommandHandler commands;

	@Test
	void updatesStatus() throws Exception {
		Lot destroyed = Lot.firstScanned("L1");
		destroyed.changeStatus(LotStatus.DESTROYED, Instant.now());
		given(commands.handle(argThat((ChangeLotStatus c) -> c != null && c.lotId().equals("L1")
				&& c.status() == LotStatus.DESTROYED))).willReturn(destroyed);

		mvc.perform(put("/api/lots/L1/status").contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"destroyed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lotId").value("L1"))
				.andExpect(jsonPath("$.status").value("destroyed"))
				.andExpect(jsonPath("$.onHold").value(false));
	}

	@Test
	void unknownStatusValueIsBadRequest() throws Exception {
		mvc.perform(put("/api/lots/L1/status").contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"bogus\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void statusOfUnknownLotIsNotFound() throws Exception {
		given(commands.handle(any(ChangeLotStatus.class)))
				.willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown lot: nope"));

		mvc.perform(put("/api/lots/nope/status").contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"complete\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void updatesHold() throws Exception {
		Lot held = Lot.firstScanned("L1");
		held.placeOnHold(Instant.now());
		given(commands.handle(argThat((SetLotHold c) -> c != null && c.lotId().equals("L1")
				&& Boolean.TRUE.equals(c.onHold())))).willReturn(held);

		mvc.perform(put("/api/lots/L1/hold").contentType(MediaType.APPLICATION_JSON)
						.content("{\"onHold\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onHold").value(true));
	}

	@Test
	void holdOfUnknownLotIsNotFound() throws Exception {
		given(commands.handle(any(SetLotHold.class)))
				.willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown lot: nope"));

		mvc.perform(put("/api/lots/nope/hold").contentType(MediaType.APPLICATION_JSON)
						.content("{\"onHold\":true}"))
				.andExpect(status().isNotFound());
	}
}
