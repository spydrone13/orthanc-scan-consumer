package com.spydrone.orthanc_scan_consumer.stage.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A row of lot_stage_next_wip_locations: a WIP location allowed in one of the stage's next stages. */
@Embeddable
class NextWipLocationEntry {

	@Column(name = "next_stage")
	private String nextStage;
	@Column(name = "wip_location")
	private String wipLocation;

	protected NextWipLocationEntry() {
	}

	NextWipLocationEntry(String nextStage, String wipLocation) {
		this.nextStage = nextStage;
		this.wipLocation = wipLocation;
	}

	String getNextStage() {
		return nextStage;
	}

	String getWipLocation() {
		return wipLocation;
	}
}
