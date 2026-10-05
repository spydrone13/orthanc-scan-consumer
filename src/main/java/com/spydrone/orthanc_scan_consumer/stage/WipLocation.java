package com.spydrone.orthanc_scan_consumer.stage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A WIP location within a stage, keyed by its id in {@link LotStage#wipLocations()}. */
public record WipLocation(String description) {

	private static final Pattern TRAILING_NUMBER = Pattern.compile("-(\\d+)$");

	/**
	 * Description for a WIP location that has none: the stage description plus the id's trailing
	 * number ("WAFER-PREP-001" in "Wafer Prep" becomes "Wafer Prep 1"), or the id itself.
	 */
	public static String defaultDescription(String stageDescription, String id) {
		Matcher m = TRAILING_NUMBER.matcher(id);
		if (stageDescription == null || !m.find()) {
			return id;
		}
		return stageDescription + " " + Long.parseLong(m.group(1));
	}
}
