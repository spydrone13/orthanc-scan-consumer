package com.spydrone.orthanc_scan_consumer.lot.application;

/** No lot exists with the given id. */
public class LotNotFoundException extends RuntimeException {

	private final String lotId;

	public LotNotFoundException(String lotId) {
		super("Unknown lot: " + lotId);
		this.lotId = lotId;
	}

	public String getLotId() {
		return lotId;
	}
}
