package com.spydrone.orthanc_scan_consumer.scan;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.spydrone.orthanc_scan_consumer.lot.application.ApplyScan;
import com.spydrone.orthanc_scan_consumer.lot.application.LotCommandHandler;

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
		Instant now = Instant.now();
		repository.save(ScanEntity.from(record, now));
		lotCommands.handle(ApplyScan.from(record, now));
	}
}
