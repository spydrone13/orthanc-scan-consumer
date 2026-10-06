package com.spydrone.orthanc_scan_consumer.lot.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.spydrone.orthanc_scan_consumer.lot.application.LotNotFoundException;

/** Turns the lot application layer's exceptions into HTTP problem details for the lot controllers. */
@RestControllerAdvice(basePackageClasses = LotApiExceptionHandler.class)
class LotApiExceptionHandler {

	@ExceptionHandler(LotNotFoundException.class)
	ProblemDetail lotNotFound(LotNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}
}
