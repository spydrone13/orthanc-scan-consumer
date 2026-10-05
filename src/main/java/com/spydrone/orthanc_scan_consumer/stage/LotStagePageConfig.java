package com.spydrone.orthanc_scan_consumer.stage;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the lot-stage editor (static/lot-stages.html) at /lot-stages. */
@Configuration
public class LotStagePageConfig implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/lot-stages").setViewName("forward:/lot-stages.html");
	}
}
