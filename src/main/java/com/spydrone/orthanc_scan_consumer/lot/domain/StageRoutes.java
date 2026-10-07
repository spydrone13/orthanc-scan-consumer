package com.spydrone.orthanc_scan_consumer.lot.domain;

/** The lot-stage graph as the scanning rules need it; implemented over the lot-stage config. */
public interface StageRoutes {

	/**
	 * Whether a lot scanned at {@code fromStage} may go to {@code toStage}, and to
	 * {@code toWipLocation} there when it's set: toStage must be a next stage of fromStage, and the
	 * WIP location one of toStage's that fromStage allows.
	 */
	boolean allows(String fromStage, String toStage, String toWipLocation);
}
