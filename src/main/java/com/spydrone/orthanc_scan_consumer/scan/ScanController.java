package com.spydrone.orthanc_scan_consumer.scan;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/scans")
public class ScanController {

	private final ScanRepository repository;

	public ScanController(ScanRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<ScanEntity> getScans() {
		return repository.findAllByOrderByReceivedAtDesc();
	}

	@GetMapping("/{clientId}")
	public ScanEntity getScan(@PathVariable String clientId) {
		return repository.findById(clientId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
}
