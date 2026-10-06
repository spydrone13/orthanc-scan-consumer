package com.spydrone.orthanc_scan_consumer.lot;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A scan as it applied to a lot: where the lot was before and where it ended up. */
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

	/** A scan that left the lot where it was; {@code scanType} is the move that was attempted. */
	public static LotStageEvent rejected(ScanRecord record, ScanType scanType, LotEntity lot, String reason,
			Instant occurredAt) {
		LotStageEvent event = of(record, scanType, lot.getCurrentStage(), lot.getWipLocation(), lot, occurredAt);
		event.rejectedReason = reason;
		return event;
	}

	/** {@code scanType} is the type as applied, not the scan's own (untrusted) scanType. */
	public static LotStageEvent of(ScanRecord record, ScanType scanType, String fromStage, String fromWipLocation,
			LotEntity after, Instant occurredAt) {
		LotStageEvent event = new LotStageEvent();
		event.clientId = record.clientId();
		event.lotId = after.getLotId();
		event.scanType = scanType;
		event.userName = record.userName();
		event.fromStage = fromStage;
		event.fromWipLocation = fromWipLocation;
		event.toStage = after.getCurrentStage();
		event.toWipLocation = after.getWipLocation();
		event.note = record.note();
		event.occurredAt = occurredAt;
		return event;
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
