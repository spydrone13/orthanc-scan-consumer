package com.spydrone.orthanc_scan_consumer.lot.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spydrone.orthanc_scan_consumer.lot.application.query.LotQueries;
import com.spydrone.orthanc_scan_consumer.lot.application.query.LotStageEventView;
import com.spydrone.orthanc_scan_consumer.lot.application.query.LotView;

@RestController
@RequestMapping("/api/lots")
public class LotQueryController {

	private final LotQueries queries;

	public LotQueryController(LotQueries queries) {
		this.queries = queries;
	}

	@GetMapping
	public List<LotView> getLots() {
		return queries.list();
	}

	@GetMapping("/{lotId}")
	public LotView getLot(@PathVariable String lotId) {
		return queries.get(lotId);
	}

	@GetMapping("/{lotId}/events")
	public List<LotStageEventView> getLotEvents(@PathVariable String lotId) {
		return queries.history(lotId);
	}
}
