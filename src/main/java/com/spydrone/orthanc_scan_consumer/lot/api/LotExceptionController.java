package com.spydrone.orthanc_scan_consumer.lot.api;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spydrone.orthanc_scan_consumer.lot.application.LotExceptionView;
import com.spydrone.orthanc_scan_consumer.lot.application.LotQueries;

/** Scans flagged for review: off-route moves and corrected lot locations. */
@RestController
@RequestMapping("/api/lot-exceptions")
public class LotExceptionController {

	private final LotQueries queries;

	public LotExceptionController(LotQueries queries) {
		this.queries = queries;
	}

	/** Newest first; {@code since} is an ISO-8601 instant, e.g. 2026-10-07T00:00:00Z. */
	@GetMapping
	public List<LotExceptionView> getExceptions(@RequestParam(required = false) Instant since) {
		return queries.exceptions(since);
	}
}
