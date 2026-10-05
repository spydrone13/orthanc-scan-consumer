package com.spydrone.orthanc_scan_consumer.lot;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;
import com.spydrone.orthanc_scan_consumer.stage.LotStageRepository;

@Service
public class LotService {

	private final LotRepository lotRepository;
	private final LotStageEventRepository eventRepository;
	private final LotStageRepository lotStageRepository;

	public LotService(LotRepository lotRepository, LotStageEventRepository eventRepository,
			LotStageRepository lotStageRepository) {
		this.lotRepository = lotRepository;
		this.eventRepository = eventRepository;
		this.lotStageRepository = lotStageRepository;
	}

	/** Moves the lot per the scan (creating it on its first scan) and records the move. */
	@Transactional
	public LotEntity apply(ScanRecord record, Instant at) {
		LotEntity lot = lotRepository.findById(record.lotId())
				.orElseGet(() -> new LotEntity(record.lotId()));
		String fromStage = lot.getCurrentStage();
		String fromWipLocation = lot.getWipLocation();

		ScanType scanType = classify(record.destination());
		lot.apply(scanType, record.currentStage(), record.destination(), at);
		lotRepository.save(lot);
		eventRepository.save(LotStageEvent.of(record, scanType, fromStage, fromWipLocation, lot, at));
		return lot;
	}

	/**
	 * The scan's own scanType can't be trusted, so it's derived from the destination: a known stage
	 * id is a stage move, anything else is a WIP location.
	 */
	private ScanType classify(String destination) {
		return destination != null && lotStageRepository.existsById(destination)
				? ScanType.TRANSITIONAL
				: ScanType.INFORMATIONAL;
	}
}
