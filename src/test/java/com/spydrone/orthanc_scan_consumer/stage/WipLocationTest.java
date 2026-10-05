package com.spydrone.orthanc_scan_consumer.stage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WipLocationTest {

	@Test
	void defaultDescriptionIsStageDescriptionPlusTrailingNumber() {
		assertThat(WipLocation.defaultDescription("Wafer Prep", "WAFER-PREP-001")).isEqualTo("Wafer Prep 1");
		assertThat(WipLocation.defaultDescription("Testing", "TESTING-120")).isEqualTo("Testing 120");
	}

	@Test
	void defaultDescriptionWithoutTrailingNumberIsTheId() {
		assertThat(WipLocation.defaultDescription("Intake", "OVERFLOW")).isEqualTo("OVERFLOW");
		assertThat(WipLocation.defaultDescription("Intake", "RACK-A")).isEqualTo("RACK-A");
	}
}
