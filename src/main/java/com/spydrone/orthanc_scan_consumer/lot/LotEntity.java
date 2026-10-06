package com.spydrone.orthanc_scan_consumer.lot;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Where a lot is now: its stage and, once scanned into one, its WIP location in that stage. */
@Entity
@Table(name = "lots")
public class LotEntity {

	@Id
	private String lotId;
	private String currentStage;
	private String wipLocation;
	/** The column default fills in lots stored before status existed. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@ColumnDefault("'ACTIVE'")
	private LotStatus status = LotStatus.ACTIVE;
	private Instant updatedAt;

	protected LotEntity() {
	}

	public LotEntity(String lotId) {
		this.lotId = lotId;
	}

	public void moveTo(String stage, String wipLocation, Instant at) {
		this.currentStage = stage;
		this.wipLocation = wipLocation;
		this.updatedAt = at;
	}

	public void setStatus(LotStatus status, Instant at) {
		this.status = status;
		this.updatedAt = at;
	}

	public String getLotId() {
		return lotId;
	}

	public String getCurrentStage() {
		return currentStage;
	}

	public String getWipLocation() {
		return wipLocation;
	}

	public LotStatus getStatus() {
		return status;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
