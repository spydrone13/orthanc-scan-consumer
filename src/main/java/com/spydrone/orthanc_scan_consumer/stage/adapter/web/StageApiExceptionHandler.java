package com.spydrone.orthanc_scan_consumer.stage.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.spydrone.orthanc_scan_consumer.stage.application.StageNotFoundException;
import com.spydrone.orthanc_scan_consumer.stage.domain.InvalidStageChangeException;

/** Turns the stage domain and application exceptions into HTTP problem details for the stage controllers. */
@RestControllerAdvice(basePackageClasses = StageApiExceptionHandler.class)
class StageApiExceptionHandler {

	@ExceptionHandler(StageNotFoundException.class)
	ProblemDetail stageNotFound(StageNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(InvalidStageChangeException.class)
	ProblemDetail invalidStageChange(InvalidStageChangeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}
}
