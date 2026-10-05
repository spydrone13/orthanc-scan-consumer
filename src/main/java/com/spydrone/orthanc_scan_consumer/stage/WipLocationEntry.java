package com.spydrone.orthanc_scan_consumer.stage;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A row of lot_stage_wip_locations. Description is null until set, then shown as the default. */
@Embeddable
public class WipLocationEntry {

	@Column(name = "wip_location")
	private String id;
	private String description;

	protected WipLocationEntry() {
	}

	public WipLocationEntry(String id, String description) {
		this.id = id;
		this.description = description;
	}

	public String getId() {
		return id;
	}

	public String getDescription() {
		return description;
	}
}
