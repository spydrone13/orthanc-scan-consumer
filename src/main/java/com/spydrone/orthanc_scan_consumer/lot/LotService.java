package com.spydrone.orthanc_scan_consumer.lot;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;

@Service
public class LotService {

	private final LotRepository lotRepository;
	private final LotStageEventRepository eventRepository;

	public LotService(LotRepository lotRepository, LotStageEventRepository eventRepository) {
		this.lotRepository = lotRepository;
		this.eventRepository = eventRepository;
	}

	/** Moves the lot per the scan (creating it on its first scan) and records the move. */
	@Transactional
	public LotEntity apply(ScanRecord record, Instant at) {
		LotEntity lot = lotRepository.findById(record.lotId())
				.orElseGet(() -> new LotEntity(record.lotId()));
		String fromStage = lot.getCurrentStage();
		String fromWipLocation = lot.getWipLocation();

		lot.apply(record.scanType(), record.currentStage(), record.destination(), at);
		lotRepository.save(lot);
		eventRepository.save(LotStageEvent.of(record, fromStage, fromWipLocation, lot, at));
		return lot;
	}
}
