package com.spydrone.orthanc_scan_consumer.lot.events;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.spydrone.orthanc_scan_consumer.lot.domain.Location;
import com.spydrone.orthanc_scan_consumer.lot.domain.LocationCorrected;
import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotHoldChanged;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotRepository;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatusChanged;
import com.spydrone.orthanc_scan_consumer.lot.domain.Scan;
import com.spydrone.orthanc_scan_consumer.lot.domain.ScanApplied;
import com.spydrone.orthanc_scan_consumer.lot.domain.ScanRejected;
import com.spydrone.orthanc_scan_consumer.lot.events.LotEventMessage.LotState;
import com.spydrone.orthanc_scan_consumer.lot.events.LotEventMessage.Place;
import com.spydrone.orthanc_scan_consumer.lot.events.LotEventMessage.ScanInfo;

import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the lot's domain events into {@link LotEventMessage}s in the outbox, in the transaction that saved
 * the lot (like {@code LotHistoryRecorder}), for {@link OutboxRelay} to publish once committed.
 */
@Component
public class LotEventRecorder {

	private final OutboxEventRepository outbox;
	private final LotRepository lots;
	private final JsonMapper json;

	public LotEventRecorder(OutboxEventRepository outbox, LotRepository lots, JsonMapper json) {
		this.outbox = outbox;
		this.lots = lots;
		this.json = json;
	}

	@EventListener
	public void on(ScanApplied event) {
		String type = event.from().equals(event.to()) ? LotEventMessage.SCANNED : LotEventMessage.MOVED;
		record(type, event.lotId(), event.at(), event.to(), place(event.from()), place(event.to()),
				scan(event.scan()), event.discrepancy() == null ? null : event.discrepancy().name(), null, null);
	}

	@EventListener
	public void on(LocationCorrected event) {
		record(LotEventMessage.LOCATION_CORRECTED, event.lotId(), event.at(), event.to(), place(event.from()),
				place(event.to()), scan(event.scan()), event.discrepancy().name(), null, null);
	}

	@EventListener
	public void on(ScanRejected event) {
		record(LotEventMessage.SCAN_REJECTED, event.lotId(), event.at(), event.location(), null,
				place(event.location()), scan(event.scan()), null, event.reason().name(), null);
	}

	@EventListener
	public void on(LotStatusChanged event) {
		record(LotEventMessage.STATUS_CHANGED, event.lotId(), event.at(), null, null, null, null, null, null,
				event.from());
	}

	@EventListener
	public void on(LotHoldChanged event) {
		record(LotEventMessage.HOLD_CHANGED, event.lotId(), event.at(), null, null, null, null, null, null, null);
	}

	/**
	 * @param location where the lot is after this event; null to take it from the saved lot. Status and
	 *        hold always come from the saved lot, since only their own events change them.
	 */
	private void record(String type, String lotId, Instant at, Location location, Place from, Place to,
			ScanInfo scan, String discrepancy, String rejectionReason, LotStatus previousStatus) {
		Lot lot = lots.findById(lotId).orElseThrow();
		Location where = location != null ? location : lot.location();
		LotEventMessage message = new LotEventMessage(UUID.randomUUID().toString(), type,
				LotEventMessage.SCHEMA_VERSION, null, at, lotId,
				new LotState(where.stage(), where.wipLocation(), lot.getStatus(), lot.isOnHold()),
				from, to, scan, discrepancy, rejectionReason, previousStatus);
		outbox.save(new OutboxEvent(message.eventId(), lotId, type, json.writeValueAsString(message), at));
	}

	private static Place place(Location location) {
		return new Place(location.stage(), location.wipLocation());
	}

	private static ScanInfo scan(Scan scan) {
		return new ScanInfo(scan.clientId(), scan.userName(), scan.scanStage(), scan.note());
	}
}
