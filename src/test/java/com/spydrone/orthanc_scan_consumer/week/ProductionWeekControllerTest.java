package com.spydrone.orthanc_scan_consumer.week;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

/** Pins the production-week API's paths and JSON shapes. */
@WebMvcTest(ProductionWeekController.class)
class ProductionWeekControllerTest {

	private static final ProductionWeekView WEEK_1 = new ProductionWeekView(2026, 1, LocalDate.of(2025, 12, 27),
			LocalDate.of(2026, 1, 2), WeekStatus.OPEN);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ProductionWeekService service;

	@Test
	void createsAYear() throws Exception {
		given(service.createYear(2026)).willReturn(List.of(WEEK_1));

		mvc.perform(post("/api/production-weeks/years/2026"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].year").value(2026))
				.andExpect(jsonPath("$[0].weekNumber").value(1))
				.andExpect(jsonPath("$[0].startDate").value("2025-12-27"))
				.andExpect(jsonPath("$[0].endDate").value("2026-01-02"))
				.andExpect(jsonPath("$[0].status").value("open"));
	}

	@Test
	void listsWithAndWithoutAYear() throws Exception {
		given(service.list(2026)).willReturn(List.of(WEEK_1));
		given(service.list(null)).willReturn(List.of(WEEK_1, WEEK_1));

		mvc.perform(get("/api/production-weeks").param("year", "2026"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get("/api/production-weeks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void getsAWeek() throws Exception {
		given(service.get(2026, 1)).willReturn(WEEK_1);
		given(service.get(2026, 60)).willThrow(
				new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown production week: 2026-W60"));

		mvc.perform(get("/api/production-weeks/2026/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.endDate").value("2026-01-02"));
		mvc.perform(get("/api/production-weeks/2026/60"))
				.andExpect(status().isNotFound());
	}

	@Test
	void closesAWeek() throws Exception {
		given(service.changeStatus(2026, 1, WeekStatus.CLOSED)).willReturn(new ProductionWeekView(2026, 1,
				LocalDate.of(2025, 12, 27), LocalDate.of(2026, 1, 2), WeekStatus.CLOSED));

		mvc.perform(put("/api/production-weeks/2026/1/status").contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"closed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("closed"));
	}

	@Test
	void setsCapacities() throws Exception {
		List<CapacityEntry> capacities = List.of(new CapacityEntry(null, null, 1200),
				new CapacityEntry(ChipType.Z_SERIES, ProcessType.A, 100));
		given(service.setCapacities(2026, 1, capacities)).willReturn(new ProductionWeekView(2026, 1,
				LocalDate.of(2025, 12, 27), LocalDate.of(2026, 1, 2), WeekStatus.OPEN, capacities));

		mvc.perform(put("/api/production-weeks/2026/1/capacities").contentType(MediaType.APPLICATION_JSON)
						.content("[{\"waferCount\":1200},"
								+ "{\"chipType\":\"Z_SERIES\",\"processType\":\"A\",\"waferCount\":100}]"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.capacities[0].chipType").value(nullValue()))
				.andExpect(jsonPath("$.capacities[0].processType").value(nullValue()))
				.andExpect(jsonPath("$.capacities[0].waferCount").value(1200))
				.andExpect(jsonPath("$.capacities[1].chipType").value("Z_SERIES"))
				.andExpect(jsonPath("$.capacities[1].processType").value("A"));
	}

	@Test
	void listedWeeksCarryCapacities() throws Exception {
		given(service.list(2026)).willReturn(List.of(new ProductionWeekView(2026, 1, LocalDate.of(2025, 12, 27),
				LocalDate.of(2026, 1, 2), WeekStatus.OPEN, List.of(new CapacityEntry(null, ProcessType.B, 300)))));

		mvc.perform(get("/api/production-weeks").param("year", "2026"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].capacities[0].processType").value("B"))
				.andExpect(jsonPath("$[0].capacities[0].waferCount").value(300));
	}

	@Test
	void invalidCapacitiesAreBadRequests() throws Exception {
		mvc.perform(put("/api/production-weeks/2026/1/capacities").contentType(MediaType.APPLICATION_JSON)
						.content("[{\"chipType\":\"A_SERIES\",\"waferCount\":1}]"))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/production-weeks/2026/1/capacities").contentType(MediaType.APPLICATION_JSON)
						.content("[null]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Capacity entries can't be null"));
		mvc.perform(put("/api/production-weeks/2026/1/capacities").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void missingOrUnknownStatusIsBadRequest() throws Exception {
		mvc.perform(put("/api/production-weeks/2026/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Status is required"));
		mvc.perform(put("/api/production-weeks/2026/1/status").contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"locked\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}
}
