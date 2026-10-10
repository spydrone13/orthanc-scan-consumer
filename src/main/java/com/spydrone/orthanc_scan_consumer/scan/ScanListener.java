package com.spydrone.orthanc_scan_consumer.scan;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.spydrone.orthanc_scan_consumer.lot.application.LotCommandHandler;
import com.spydrone.orthanc_scan_consumer.lot.application.command.ApplyScan;

@Component
public class ScanListener {

	private static final Logger log = LoggerFactory.getLogger(ScanListener.class);

	private final ScanRepository repository;
	private final LotCommandHandler lotCommands;

	public ScanListener(ScanRepository repository, LotCommandHandler lotCommands) {
		this.repository = repository;
		this.lotCommands = lotCommands;
	}

	/**
	 * Stores each scan once by clientId and applies it to its lot; RabbitMQ is at-least-once, so
	 * redeliveries are skipped.
	 */
	@RabbitListener(queues = "${app.scans.queue}")
	@Transactional
	public void onScan(ScanRecord record) {
		if (repository.existsById(record.clientId())) {
			log.info("Skipping duplicate scan {}", record.clientId());
			return;
		}
		if (record.lotId().equals("LOT-013")) {
			throw new RuntimeException("Simulated exception for testing! Lot id: LOT-013");
		}
		Instant now = Instant.now();
		repository.save(ScanEntity.from(record, now));
		lotCommands.handle(ApplyScan.from(record, now));
	}
}
