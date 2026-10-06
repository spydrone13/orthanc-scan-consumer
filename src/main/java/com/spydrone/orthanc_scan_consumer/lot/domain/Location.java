package com.spydrone.orthanc_scan_consumer.lot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Where a lot is: a stage and, once scanned into one, a WIP location within it. */
@Embeddable
public record Location(
		@Column(name = "current_stage") String stage,
		@Column(name = "wip_location") String wipLocation) {

	/** A lot that hasn't been scanned yet. */
	public static final Location NOWHERE = new Location(null, null);
}
