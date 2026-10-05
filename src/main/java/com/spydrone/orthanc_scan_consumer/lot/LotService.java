package com.spydrone.orthanc_scan_consumer.lot;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

@Service
public class LotService {

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
		lot.moveTo(toStage, toWipLocation, at);
		lotRepository.save(lot);
		eventRepository.save(LotStageEvent.of(record, scanType, fromStage, fromWipLocation, lot, at));
		return lot;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
