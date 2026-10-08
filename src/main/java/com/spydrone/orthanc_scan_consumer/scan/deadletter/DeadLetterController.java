package com.spydrone.orthanc_scan_consumer.scan.deadletter;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Scans in the dead-letter queue, by {@link DeadLetter#id()}. */
@RestController
@RequestMapping("/api/dead-letters")
public class DeadLetterController {

	private final DeadLetterService service;

	public DeadLetterController(DeadLetterService service) {
		this.service = service;
	}

	@GetMapping
	public DeadLetterService.DeadLetters list() {
		return service.list();
	}

	@PostMapping("/{id}/retry")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void retry(@PathVariable String id) {
		requireFound(service.retry(deadLetter -> deadLetter.id().equals(id)), id);
	}

	@PostMapping("/retry")
	public Map<String, Integer> retryAll() {
		return Map.of("replayed", service.retry(deadLetter -> true));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void discard(@PathVariable String id) {
		requireFound(service.discard(deadLetter -> deadLetter.id().equals(id)), id);
	}

	private static void requireFound(int count, String id) {
		if (count == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No dead-lettered scan " + id);
		}
	}
}
