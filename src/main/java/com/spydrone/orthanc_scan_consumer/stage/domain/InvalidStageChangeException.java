package com.spydrone.orthanc_scan_consumer.stage.domain;

/** A change to a stage breaks one of the stage rules; the message says which. */
public class InvalidStageChangeException extends RuntimeException {

	public InvalidStageChangeException(String message) {
		super(message);
	}
}
