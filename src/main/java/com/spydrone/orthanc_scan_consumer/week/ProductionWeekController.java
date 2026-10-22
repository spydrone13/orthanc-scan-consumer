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

@RestController
@RequestMapping("/api/production-weeks")
public class ProductionWeekController {

	private final ProductionWeekService service;

	public ProductionWeekController(ProductionWeekService service) {
		this.service = service;
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
}
