package com.spydrone.orthanc_scan_consumer.scan.deadletter;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the failed-scans page (static/dead-letters.html) at /dead-letters. */
@Configuration
public class DeadLetterPageConfig implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/dead-letters").setViewName("forward:/dead-letters.html");
	}
}
