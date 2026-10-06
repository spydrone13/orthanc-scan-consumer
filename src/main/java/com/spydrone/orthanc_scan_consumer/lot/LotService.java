package com.spydrone.orthanc_scan_consumer.lot;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

@Service
public class LotService {

	private static final Logger log = LoggerFactory.getLogger(LotService.class);
	/** Same code the producer returns to the UI for a held lot. */
	static final String LOT_ON_HOLD = "LOT_ON_HOLD";

	private final LotRepository lotRepository;
	private final LotStageEventRepository eventRepository;

	public LotService(LotRepository lotRepository, LotStageEventRepository eventRepository) {
		this.lotRepository = lotRepository;
		this.eventRepository = eventRepository;
	}

	/**
	 * Moves the lot per the scan (creating it on its first scan) and records the move. The scan's
	 * own scanType isn't trusted; the move comes from its destination fields:
	 * <ul>
	 * <li>stage and WIP location: into that WIP location in that stage
	 * <li>stage only: into that stage, no WIP location
	 * <li>WIP location only: into that WIP location in the scan's current stage
	 * <li>neither: the lot stays where it is
	 * </ul>
	 */
	@Transactional
	public LotEntity apply(ScanRecord record, Instant at) {
		LotEntity lot = lotRepository.findById(record.lotId())
				.orElseGet(() -> new LotEntity(record.lotId()));
		if (lot.getStatus() != LotStatus.ACTIVE) {
			log.warn("Applying scan {} to lot {} with status {}", record.clientId(), lot.getLotId(), lot.getStatus());
		}
		String fromStage = lot.getCurrentStage();
		String fromWipLocation = lot.getWipLocation();

		String destinationStage = blankToNull(record.destinationStage());
		String destinationWipLocation = blankToNull(record.destinationWipLocation());
		String toStage;
		String toWipLocation;
		if (destinationStage != null) {
			toStage = destinationStage;
			toWipLocation = destinationWipLocation;
		}
		else if (destinationWipLocation != null || fromStage == null) {
			toStage = record.currentStage();
			toWipLocation = destinationWipLocation;
		}
		else {
			toStage = fromStage;
			toWipLocation = fromWipLocation;
		}

		ScanType scanType = Objects.equals(toStage, record.currentStage())
				? ScanType.INFORMATIONAL
				: ScanType.TRANSITIONAL;
		// The producer rejects these up front; this catches a hold placed while the scan was queued.
		if (lot.isOnHold() && !Objects.equals(toStage, fromStage)) {
			log.warn("Not applying scan {}: lot {} is on hold in {}", record.clientId(), lot.getLotId(), fromStage);
			eventRepository.save(LotStageEvent.rejected(record, scanType, lot, LOT_ON_HOLD, at));
			return lot;
		}
		lot.moveTo(toStage, toWipLocation, at);
		lotRepository.save(lot);
		eventRepository.save(LotStageEvent.of(record, scanType, fromStage, fromWipLocation, lot, at));
		return lot;
	}

	/** Sets the lot's status; any status may change to any other. */
	@Transactional
	public LotEntity updateStatus(String lotId, LotStatus status, Instant at) {
		if (status == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
		}
		LotEntity lot = lotRepository.findById(lotId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown lot: " + lotId));
		lot.setStatus(status, at);
		return lotRepository.save(lot);
	}

	/** Places the lot on hold or releases it. A held lot can't be scanned out of its current stage. */
	@Transactional
	public LotEntity updateHold(String lotId, Boolean onHold, Instant at) {
		if (onHold == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "onHold is required");
		}
		LotEntity lot = lotRepository.findById(lotId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown lot: " + lotId));
		lot.setOnHold(onHold, at);
		return lotRepository.save(lot);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
