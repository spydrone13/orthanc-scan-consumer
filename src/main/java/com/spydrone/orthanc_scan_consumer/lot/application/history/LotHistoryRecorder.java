package com.spydrone.orthanc_scan_consumer.lot.application.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.spydrone.orthanc_scan_consumer.lot.domain.LocationCorrected;
import com.spydrone.orthanc_scan_consumer.lot.domain.ScanApplied;
import com.spydrone.orthanc_scan_consumer.lot.domain.ScanRejected;

/** Writes each scan's outcome to the lot's history, in the transaction that saved the lot. */
@Component
public class LotHistoryRecorder {

	private static final Logger log = LoggerFactory.getLogger(LotHistoryRecorder.class);

	private final LotStageEventRepository history;

	public LotHistoryRecorder(LotStageEventRepository history) {
		this.history = history;
	}

	@EventListener
	public void on(ScanApplied event) {
		if (event.discrepancy() != null) {
			log.warn("Scan {} moved lot {} from {} to {}: {}", event.scan().clientId(), event.lotId(), event.from(),
					event.to(), event.discrepancy());
		}
		history.save(LotStageEvent.applied(event));
	}

	@EventListener
	public void on(LocationCorrected event) {
		log.warn("Scan {} found lot {} at {}, not {}: {}", event.scan().clientId(), event.lotId(), event.to(),
				event.from(), event.discrepancy());
		history.save(LotStageEvent.corrected(event));
	}

	/** The producer rejects most of these up front; these slipped through while queued. */
	@EventListener
	public void on(ScanRejected event) {
		log.warn("Not applying scan {} to lot {}: {}", event.scan().clientId(), event.lotId(), event.reason());
		history.save(LotStageEvent.rejected(event));
	}
}
