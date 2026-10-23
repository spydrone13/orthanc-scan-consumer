package com.spydrone.orthanc_scan_consumer.stage.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A row of lot_stage_wip_locations. Description is null until set, then shown as the default. */
@Embeddable
class WipLocationEntry {

	@Column(name = "wip_location")
	private String id;
	private String description;

	protected WipLocationEntry() {
	}

	WipLocationEntry(String id, String description) {
		this.id = id;
		this.description = description;
	}

	String getId() {
		return id;
	}

	String getDescription() {
		return description;
	}
}
