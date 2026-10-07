package com.spydrone.orthanc_scan_consumer.lot.domain;

/**
 * Why a scan was flagged for review. Flagged scans are still applied: the scan's stage is where the
 * lot physically is, so the records follow it.
 */
public enum Discrepancy {
	/** Scanned to a stage that isn't a next stage of the scan's stage, or to a WIP location not allowed there. */
	OFF_ROUTE,
	/** The records had the lot at another stage; the operator confirmed it was here and gave a reason. */
	LOCATION_CORRECTED,
	/** The records had the lot at another stage, and nobody confirmed it when scanning. */
	LOCATION_MISMATCH_UNCONFIRMED
}
