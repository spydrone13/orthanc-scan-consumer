package com.spydrone.orthanc_scan_consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// In-memory DB so tests don't write to the app's ./data file database.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:context-test")
class OrthancScanConsumerApplicationTests {

	@Test
	void contextLoads() {
	}

}
