package com.spydrone.orthanc_scan_consumer.week;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the production-weeks page (static/production-weeks.html) at /production-weeks. */
@Configuration
public class ProductionWeekPageConfig implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/production-weeks").setViewName("forward:/production-weeks.html");
	}
}
