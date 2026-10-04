package com.spydrone.orthanc_scan_consumer.scan;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ScanListener {

	private static final Logger log = LoggerFactory.getLogger(ScanListener.class);

	private final ScanRepository repository;

	public ScanListener(ScanRepository repository) {
		this.repository = repository;
	}

	/** Stores each scan once by clientId; RabbitMQ is at-least-once, so redeliveries are skipped. */
	@RabbitListener(queues = "${app.scans.queue}")
	@Transactional
	public void onScan(ScanRecord record) {
		if (repository.existsById(record.clientId())) {
			log.info("Skipping duplicate scan {}", record.clientId());
			return;
		}
		repository.save(ScanEntity.from(record, Instant.now()));
	}
}
