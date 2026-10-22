package com.spydrone.orthanc_scan_consumer.lot.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.spydrone.orthanc_scan_consumer.lot.application.LotCommandHandler;
import com.spydrone.orthanc_scan_consumer.lot.application.LotNotFoundException;
import com.spydrone.orthanc_scan_consumer.lot.application.command.ChangeLotStatus;
import com.spydrone.orthanc_scan_consumer.lot.application.command.SetLotDetails;
import com.spydrone.orthanc_scan_consumer.lot.application.command.SetLotHold;
import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotDetails;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

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
				.willThrow(new LotNotFoundException("nope"));

		mvc.perform(put("/api/lots/nope/status").contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"complete\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Unknown lot: nope"));
	}

	@Test
	void updatesHold() throws Exception {
		Lot held = Lot.firstScanned("L1");
		held.placeOnHold(Instant.now());
		given(commands.handle(argThat((SetLotHold c) -> c != null && c.lotId().equals("L1")
				&& c.onHold()))).willReturn(held);

		mvc.perform(put("/api/lots/L1/hold").contentType(MediaType.APPLICATION_JSON)
						.content("{\"onHold\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onHold").value(true));
	}

	@Test
	void holdOfUnknownLotIsNotFound() throws Exception {
		given(commands.handle(any(SetLotHold.class)))
				.willThrow(new LotNotFoundException("nope"));

		mvc.perform(put("/api/lots/nope/hold").contentType(MediaType.APPLICATION_JSON)
						.content("{\"onHold\":true}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Unknown lot: nope"));
	}

	@Test
	void updatesDetails() throws Exception {
		LotDetails details = new LotDetails(LocalDate.of(2026, 12, 15), 25, ChipType.Z_SERIES, ProcessType.B);
		Lot lot = Lot.registered("L1");
		lot.changeDetails(details, Instant.now());
		given(commands.handle(argThat((SetLotDetails c) -> c != null && c.lotId().equals("L1")
				&& c.details().equals(details)))).willReturn(lot);

		mvc.perform(put("/api/lots/L1/details").contentType(MediaType.APPLICATION_JSON)
						.content("{\"contractShipDate\":\"2026-12-15\",\"waferCount\":25,\"chipType\":\"Z_SERIES\","
								+ "\"processType\":\"B\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lotId").value("L1"))
				.andExpect(jsonPath("$.currentStage").doesNotExist())
				.andExpect(jsonPath("$.contractShipDate").value("2026-12-15"))
				.andExpect(jsonPath("$.waferCount").value(25))
				.andExpect(jsonPath("$.chipType").value("Z_SERIES"))
				.andExpect(jsonPath("$.processType").value("B"));
	}

	@Test
	void invalidDetailsAreBadRequests() throws Exception {
		mvc.perform(put("/api/lots/L1/details").contentType(MediaType.APPLICATION_JSON)
						.content("{\"waferCount\":-1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("waferCount can't be negative"));
		mvc.perform(put("/api/lots/L1/details").contentType(MediaType.APPLICATION_JSON)
						.content("{\"chipType\":\"A_SERIES\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/lots/L1/details").contentType(MediaType.APPLICATION_JSON)
						.content("{\"processType\":\"D\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/lots/L1/details").contentType(MediaType.APPLICATION_JSON)
						.content("{\"contractShipDate\":\"15/12/2026\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(commands);
	}

	@Test
	void missingValuesAreBadRequests() throws Exception {
		mvc.perform(put("/api/lots/L1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Status is required"));
		mvc.perform(put("/api/lots/L1/hold").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("onHold is required"));
		verifyNoInteractions(commands);
	}
}
