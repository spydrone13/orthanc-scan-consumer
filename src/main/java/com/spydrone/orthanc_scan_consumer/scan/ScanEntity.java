package com.spydrone.orthanc_scan_consumer.scan;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scans")
public class ScanEntity {

	@Id
	private String clientId;
	private String userName;
	private String currentStage;
	private String lotId;
	private String destinationStage;
	private String destinationWipLocation;
	/** A {@link ScanType} name, kept as a plain string so the column accepts values added later (see SchemaTest). */
	private String scanType;
	@Column(length = 2000)
	private String note;
	@Column(length = 2000)
	private String correctionReason;
	private Instant receivedAt;

	protected ScanEntity() {
	}

	public static ScanEntity from(ScanRecord record, Instant receivedAt) {
		ScanEntity entity = new ScanEntity();
		entity.clientId = record.clientId();
		entity.userName = record.userName();
		entity.currentStage = record.currentStage();
		entity.lotId = record.lotId();
		entity.destinationStage = record.destinationStage();
		entity.destinationWipLocation = record.destinationWipLocation();
		entity.scanType = record.scanType() == null ? null : record.scanType().name();
		entity.note = record.note();
		entity.correctionReason = record.correctionReason();
		entity.receivedAt = receivedAt;
		return entity;
	}

	public String getClientId() {
		return clientId;
	}

	public String getUserName() {
		return userName;
	}

	public String getCurrentStage() {
		return currentStage;
	}

	public String getLotId() {
		return lotId;
	}

	public String getDestinationStage() {
		return destinationStage;
	}

	public String getDestinationWipLocation() {
		return destinationWipLocation;
	}

	public ScanType getScanType() {
		return scanType == null ? null : ScanType.valueOf(scanType);
	}

	public String getNote() {
		return note;
	}

	public String getCorrectionReason() {
		return correctionReason;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}
}
