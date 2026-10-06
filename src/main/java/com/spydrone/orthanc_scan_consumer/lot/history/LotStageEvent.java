package com.spydrone.orthanc_scan_consumer.lot.history;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.lot.domain.Location;
import com.spydrone.orthanc_scan_consumer.lot.domain.Scan;
import com.spydrone.orthanc_scan_consumer.lot.domain.ScanApplied;
import com.spydrone.orthanc_scan_consumer.lot.domain.ScanRejected;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A row of a lot's scan history, written from the lot's domain events. */
@Entity
@Table(name = "lot_stage_events")
public class LotStageEvent {

	/** Same id as the scan this event came from. */
	@Id
	private String clientId;
	private String lotId;
	@Enumerated(EnumType.STRING)
	private ScanType scanType;
	private String userName;
	private String fromStage;
	private String fromWipLocation;
	private String toStage;
	private String toWipLocation;
	@Column(length = 2000)
	private String note;
	private Instant occurredAt;
	/** Set when the scan was not applied (e.g. LOT_ON_HOLD); the to-values then equal the from-values. */
	private String rejectedReason;

	protected LotStageEvent() {
	}

	public static LotStageEvent applied(ScanApplied event) {
		return of(event.lotId(), event.scan(), event.scanType(), event.from(), event.to(), event.at(), null);
	}

	public static LotStageEvent rejected(ScanRejected event) {
		return of(event.lotId(), event.scan(), event.scanType(), event.location(), event.location(), event.at(),
				event.reason().name());
	}

	private static LotStageEvent of(String lotId, Scan scan, ScanType scanType, Location from, Location to,
			Instant occurredAt, String rejectedReason) {
		LotStageEvent row = new LotStageEvent();
		row.clientId = scan.clientId();
		row.lotId = lotId;
		row.scanType = scanType;
		row.userName = scan.userName();
		row.fromStage = from.stage();
		row.fromWipLocation = from.wipLocation();
		row.toStage = to.stage();
		row.toWipLocation = to.wipLocation();
		row.note = scan.note();
		row.occurredAt = occurredAt;
		row.rejectedReason = rejectedReason;
		return row;
	}

	public String getClientId() {
		return clientId;
	}

	public String getLotId() {
		return lotId;
	}

	public ScanType getScanType() {
		return scanType;
	}

	public String getUserName() {
		return userName;
	}

	public String getFromStage() {
		return fromStage;
	}

	public String getFromWipLocation() {
		return fromWipLocation;
	}

	public String getToStage() {
		return toStage;
	}

	public String getToWipLocation() {
		return toWipLocation;
	}

	public String getNote() {
		return note;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getRejectedReason() {
		return rejectedReason;
	}
}
