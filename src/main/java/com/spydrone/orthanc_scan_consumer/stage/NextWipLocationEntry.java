package com.spydrone.orthanc_scan_consumer.stage;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A row of lot_stage_next_wip_locations: a WIP location allowed in one of the stage's next stages. */
@Embeddable
public class NextWipLocationEntry {

	@Column(name = "next_stage")
	private String nextStage;
	@Column(name = "wip_location")
	private String wipLocation;

	protected NextWipLocationEntry() {
	}

	public NextWipLocationEntry(String nextStage, String wipLocation) {
		this.nextStage = nextStage;
		this.wipLocation = wipLocation;
	}

	public String getNextStage() {
		return nextStage;
	}

	public String getWipLocation() {
		return wipLocation;
	}
}
