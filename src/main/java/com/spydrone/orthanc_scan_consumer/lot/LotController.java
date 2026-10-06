package com.spydrone.orthanc_scan_consumer.lot;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/lots")
public class LotController {

	private final LotRepository lotRepository;
	private final LotStageEventRepository eventRepository;
	private final LotService lotService;

	public LotController(LotRepository lotRepository, LotStageEventRepository eventRepository,
			LotService lotService) {
		this.lotRepository = lotRepository;
		this.eventRepository = eventRepository;
		this.lotService = lotService;
	}

	@GetMapping
	public List<LotEntity> getLots() {
		return lotRepository.findAllByOrderByUpdatedAtDesc();
	}

	@GetMapping("/{lotId}")
	public LotEntity getLot(@PathVariable String lotId) {
		return lotRepository.findById(lotId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	@GetMapping("/{lotId}/events")
	public List<LotStageEvent> getLotEvents(@PathVariable String lotId) {
		if (!lotRepository.existsById(lotId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return eventRepository.findByLotIdOrderByOccurredAtDesc(lotId);
	}

	@PutMapping("/{lotId}/status")
	public LotEntity updateStatus(@PathVariable String lotId, @RequestBody LotStatusUpdate update) {
		return lotService.updateStatus(lotId, update.status(), Instant.now());
	}
}
