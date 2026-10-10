package com.spydrone.orthanc_scan_consumer.lot.application.events;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.lot.domain.LotStatus;

/**
 * What other applications receive on the {@code orthanc.lots} exchange, as JSON. The routing key is
 * {@link #type()}. This is a published contract: add fields rather than rename or remove them, and bump
 * {@link #SCHEMA_VERSION} for a change subscribers must handle. Fields that don't apply to a type are null.
 *
 * @param eventId unique per event; delivery is at-least-once, so subscribers de-duplicate on it
 * @param sequence increases with every event (set when published), so a subscriber can ignore an event
 *        older than one it has already applied for the same lot
 * @param lot the lot's state right after this event
 * @param from for moves and corrections, where the lot was
 * @param to for moves and corrections, where it is now; for a rejected scan, where it stayed
 * @param scan the scan behind a scan event; null for status and hold changes
 * @param discrepancy why a move or correction was flagged for review (a {@code Discrepancy} name), if it was
 * @param rejectionReason why a scan wasn't applied (a {@code RejectionReason} name)
 * @param previousStatus for a status change, the status before it (written like {@code lot.status})
 */
public record LotEventMessage(
		String eventId,
		String type,
		int schemaVersion,
		Long sequence,
		Instant occurredAt,
		String lotId,
		LotState lot,
		Place from,
		Place to,
		ScanInfo scan,
		String discrepancy,
		String rejectionReason,
		LotStatus previousStatus) {

	public static final int SCHEMA_VERSION = 1;

	/** A scan moved the lot to another stage or WIP location. */
	public static final String MOVED = "lot.moved";
	/** A scan was applied but the lot stayed where it was. */
	public static final String SCANNED = "lot.scanned";
	/** The records had the lot at another stage than it was scanned at, so it was moved there first. */
	public static final String LOCATION_CORRECTED = "lot.location-corrected";
	/** A scan wasn't applied (lot on hold, or not active); the lot stayed where it was. */
	public static final String SCAN_REJECTED = "lot.scan-rejected";
	public static final String STATUS_CHANGED = "lot.status-changed";
	/** Placed on hold or released; see {@code lot.onHold}. */
	public static final String HOLD_CHANGED = "lot.hold-changed";

	/** @param status written as in the lot API: "active", "complete", ... */
	public record LotState(String currentStage, String wipLocation, LotStatus status, boolean onHold) {
	}

	public record Place(String stage, String wipLocation) {
	}

	/** @param scanStage the stage the operator scanned from */
	public record ScanInfo(String clientId, String userName, String scanStage, String note) {
	}

	LotEventMessage withSequence(long sequence) {
		return new LotEventMessage(eventId, type, schemaVersion, sequence, occurredAt, lotId, lot, from, to, scan,
				discrepancy, rejectionReason, previousStatus);
	}
}
