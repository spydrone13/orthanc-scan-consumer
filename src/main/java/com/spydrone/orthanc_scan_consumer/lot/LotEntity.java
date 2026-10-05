package com.spydrone.orthanc_scan_consumer.lot;

import java.time.Instant;

import com.spydrone.orthanc_scan_consumer.scan.ScanType;

import jakarta.persistence.Entity;
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
	private Instant updatedAt;

	protected LotEntity() {
	}

	public LotEntity(String lotId) {
		this.lotId = lotId;
	}

	/**
	 * Applies a scan as-is, without checking it against the stage graph. A transitional scan moves
	 * the lot to the destination stage (no WIP location yet); an informational scan places it at the
	 * destination WIP location within the scan's current stage.
	 */
	public void apply(ScanType scanType, String scanStage, String destination, Instant at) {
		if (scanType == ScanType.TRANSITIONAL) {
			this.currentStage = destination;
			this.wipLocation = null;
		}
		else {
			this.currentStage = scanStage;
			this.wipLocation = destination;
		}
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

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
