package com.spydrone.orthanc_scan_consumer.week;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.spydrone.orthanc_scan_consumer.lot.application.query.LotView;
import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;
import com.spydrone.orthanc_scan_consumer.week.ScheduledWeekView.CapacityUsage;

/** Pins the production-week API's paths and JSON shapes. */
@WebMvcTest(ProductionWeekController.class)
class ProductionWeekControllerTest {

	private static final ProductionWeekView WEEK_1 = new ProductionWeekView(2026, 1, LocalDate.of(2025, 12, 27),
			LocalDate.of(2026, 1, 2), WeekStatus.OPEN);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ProductionWeekService service;

	@MockitoBean
	private ProductionWeekScheduleService schedule;

	@Test
	void listsScheduledWafers() throws Exception {
		given(schedule.scheduled(2027)).willReturn(List.of(new ScheduledWeekView(2, 3, 820, List.of(
				new CapacityUsage(null, null, 1200, 820),
				new CapacityUsage(ChipType.Z_SERIES, ProcessType.A, 100, 150)))));

		mvc.perform(get("/api/production-weeks/scheduled").param("year", "2027"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].weekNumber").value(2))
				.andExpect(jsonPath("$[0].lots").value(3))
				.andExpect(jsonPath("$[0].scheduledWafers").value(820))
				.andExpect(jsonPath("$[0].capacities[0].chipType").value(nullValue()))
				.andExpect(jsonPath("$[0].capacities[0].capacity").value(1200))
				.andExpect(jsonPath("$[0].capacities[0].scheduled").value(820))
				.andExpect(jsonPath("$[0].capacities[1].chipType").value("Z_SERIES"))
				.andExpect(jsonPath("$[0].capacities[1].processType").value("A"))
				.andExpect(jsonPath("$[0].capacities[1].scheduled").value(150));
	}

	@Test
	void checksWhetherALotCanBeScheduled() throws Exception {
		given(schedule.check(2027, 2, "L1", 30, ChipType.Z_SERIES, null)).willReturn(new ScheduleCheckView(2027, 2,
				false, List.of(ScheduleCheckView.Reason.OVER_CAPACITY),
				new ScheduleCheckView.LotCandidate("L1", 30, ChipType.Z_SERIES, ProcessType.A, false),
				List.of(new ScheduleCheckView.CapacityCheck(ChipType.Z_SERIES, null, 500, 480, 510, false))));

		mvc.perform(get("/api/production-weeks/2027/2/schedule-check")
						.param("lotId", "L1").param("waferCount", "30").param("chipType", "Z_SERIES"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canSchedule").value(false))
				.andExpect(jsonPath("$.reasons[0]").value("OVER_CAPACITY"))
				.andExpect(jsonPath("$.lot.lotId").value("L1"))
				.andExpect(jsonPath("$.lot.processType").value("A"))
				.andExpect(jsonPath("$.lot.alreadyScheduled").value(false))
				.andExpect(jsonPath("$.capacities[0].chipType").value("Z_SERIES"))
				.andExpect(jsonPath("$.capacities[0].processType").value(nullValue()))
				.andExpect(jsonPath("$.capacities[0].scheduled").value(480))
				.andExpect(jsonPath("$.capacities[0].afterScheduling").value(510))
				.andExpect(jsonPath("$.capacities[0].fits").value(false));
	}

	@Test
	void listsTheWeeksLots() throws Exception {
		given(schedule.lots(2027, 2)).willReturn(List.of(new LotView("UI-1", "wafer-prep", "WAFER-PREP-001",
				LotStatus.ACTIVE, true, LocalDate.of(2027, 1, 2), 350, ChipType.Z_SERIES, ProcessType.A,
				Instant.parse("2026-10-22T12:00:00Z"), null)));

		mvc.perform(get("/api/production-weeks/2027/2/lots"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].lotId").value("UI-1"))
				.andExpect(jsonPath("$[0].currentStage").value("wafer-prep"))
				.andExpect(jsonPath("$[0].wipLocation").value("WAFER-PREP-001"))
				.andExpect(jsonPath("$[0].status").value("active"))
				.andExpect(jsonPath("$[0].onHold").value(true))
				.andExpect(jsonPath("$[0].contractShipDate").value("2027-01-02"))
				.andExpect(jsonPath("$[0].waferCount").value(350))
				.andExpect(jsonPath("$[0].chipType").value("Z_SERIES"))
				.andExpect(jsonPath("$[0].processType").value("A"));
	}

	@Test
	void scheduleCheckRejectsUnknownTypes() throws Exception {
		mvc.perform(get("/api/production-weeks/2027/2/schedule-check")
						.param("waferCount", "5").param("chipType", "A_SERIES"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(schedule);
	}

	@Test
	void scheduledNeedsAYear() throws Exception {
		mvc.perform(get("/api/production-weeks/scheduled"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(schedule);
	}

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
