package com.spydrone.orthanc_scan_consumer.lot.history;

import java.time.Duration;
import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.lot.domain.Discrepancy;
import com.spydrone.orthanc_scan_consumer.lot.domain.LocationCorrected;
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

	/** Suffix on the scan's clientId for the correction row a scan can add before its own row. */
	public static final String CORRECTION_SUFFIX = "#correction";
	/** A correction is recorded just before the scan that caused it, so newest-first history lists it second. */
	private static final Duration CORRECTION_OFFSET = Duration.ofNanos(1_000);

	/** Same id as the scan this event came from; a correction row adds {@link #CORRECTION_SUFFIX}. */
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
	/** The scan's note; for a correction row, the operator's reason (null if unconfirmed). */
	@Column(length = 2000)
	private String note;
	private Instant occurredAt;
	/** Set when the scan was not applied (e.g. LOT_ON_HOLD); the to-values then equal the from-values. */
	private String rejectedReason;
	/** Set when the row is flagged for review; see {@link Discrepancy}. */
	@Enumerated(EnumType.STRING)
	private Discrepancy exception;
	/** For a correction row: the scan that put the lot where the records wrongly had it. */
	private String correctsClientId;

	protected LotStageEvent() {
	}

	public static LotStageEvent applied(ScanApplied event) {
		LotStageEvent row = of(event.lotId(), event.scan(), event.scanType(), event.from(), event.to(), event.at(),
				null);
		row.exception = event.discrepancy();
		return row;
	}

	public static LotStageEvent rejected(ScanRejected event) {
		return of(event.lotId(), event.scan(), event.scanType(), event.location(), event.location(), event.at(),
				event.reason().name());
	}

	public static LotStageEvent corrected(LocationCorrected event) {
		LotStageEvent row = of(event.lotId(), event.scan(), ScanType.CORRECTION, event.from(), event.to(),
				event.at().minus(CORRECTION_OFFSET), null);
		row.clientId = event.scan().clientId() + CORRECTION_SUFFIX;
		row.note = event.scan().correctionReason();
		row.exception = event.discrepancy();
		row.correctsClientId = event.correctsClientId();
		return row;
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

	public Discrepancy getException() {
		return exception;
	}

	public String getCorrectsClientId() {
		return correctsClientId;
	}
}
