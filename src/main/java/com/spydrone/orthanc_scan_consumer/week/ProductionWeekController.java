package com.spydrone.orthanc_scan_consumer.week;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.lot.application.query.LotView;
import com.spydrone.orthanc_scan_consumer.lot.domain.ChipType;
import com.spydrone.orthanc_scan_consumer.lot.domain.ProcessType;

@RestController
@RequestMapping("/api/production-weeks")
public class ProductionWeekController {

	private final ProductionWeekService service;
	private final ProductionWeekScheduleService schedule;

	public ProductionWeekController(ProductionWeekService service, ProductionWeekScheduleService schedule) {
		this.service = service;
		this.schedule = schedule;
	}

	/**
	 * Wafers scheduled against each week's capacity. Separate from the weeks list so the page can show the
	 * weeks first and fill this in when it arrives.
	 */
	@GetMapping("/scheduled")
	public List<ScheduledWeekView> scheduled(@RequestParam int year) {
		return schedule.scheduled(year);
	}

	/** Creates the year's missing weeks, open, and returns all of the year's weeks. */
	@PostMapping("/years/{year}")
	public List<ProductionWeekView> createYear(@PathVariable int year) {
		return service.createYear(year);
	}

	@GetMapping
	public List<ProductionWeekView> list(@RequestParam(required = false) Integer year) {
		return service.list(year);
	}

	/**
	 * Whether a lot fits in the week's capacity. Give lotId for a stored lot, the details for one that isn't
	 * stored yet, or both to override the stored details. Changes nothing.
	 */
	@GetMapping("/{year}/{week}/schedule-check")
	public ScheduleCheckView scheduleCheck(@PathVariable int year, @PathVariable int week,
			@RequestParam(required = false) String lotId, @RequestParam(required = false) Integer waferCount,
			@RequestParam(required = false) ChipType chipType,
			@RequestParam(required = false) ProcessType processType) {
		return schedule.check(year, week, lotId, waferCount, chipType, processType);
	}

	/** The lots scheduled in the week: active, with their contract ship date in it. */
	@GetMapping("/{year}/{week}/lots")
	public List<LotView> lots(@PathVariable int year, @PathVariable int week) {
		return schedule.lots(year, week);
	}

	@GetMapping("/{year}/{week}")
	public ProductionWeekView get(@PathVariable int year, @PathVariable int week) {
		return service.get(year, week);
	}

	@PutMapping("/{year}/{week}/status")
	public ProductionWeekView updateStatus(@PathVariable int year, @PathVariable int week,
			@RequestBody ProductionWeekStatusUpdate update) {
		if (update.status() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
		}
		return service.changeStatus(year, week, update.status());
	}

	/** Replaces the week's capacity rules; an empty array clears them. */
	@PutMapping("/{year}/{week}/capacities")
	public ProductionWeekView updateCapacities(@PathVariable int year, @PathVariable int week,
			@RequestBody List<CapacityEntry> capacities) {
		if (capacities.contains(null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacity entries can't be null");
		}
		return service.setCapacities(year, week, capacities);
	}
}
