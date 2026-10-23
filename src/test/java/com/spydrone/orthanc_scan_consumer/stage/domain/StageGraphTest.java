package com.spydrone.orthanc_scan_consumer.stage.domain;

import static com.spydrone.orthanc_scan_consumer.stage.domain.StageTest.stage;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class StageGraphTest {

	private final Stage wetEtching = stage("wet-etching", "Wet Etching", List.of(), "WET-ETCHING-001", "WET-ETCHING-002");
	private final Stage dryEtching = stage("dry-etching", "Dry Etching", List.of(), "DRY-ETCHING-001");

	@Test
	void keepsStagesInTheGivenOrder() {
		StageGraph graph = new StageGraph(List.of(wetEtching, dryEtching));

		assertThat(graph.stages()).extracting(Stage::id).containsExactly("wet-etching", "dry-etching");
		assertThat(graph.find("dry-etching")).containsSame(dryEtching);
		assertThat(graph.find("nope")).isEmpty();
	}

	@Test
	void allowsAnyWipLocationOfANextStageThatIsNotRestricted() {
		StageGraph graph = new StageGraph(List.of(
				stage("photolithography", "Photolithography", List.of("wet-etching")), wetEtching, dryEtching));

		assertThat(graph.allows("photolithography", "wet-etching", null)).isTrue();
		assertThat(graph.allows("photolithography", "wet-etching", "WET-ETCHING-002")).isTrue();
		assertThat(graph.allows("photolithography", "wet-etching", "DRY-ETCHING-001")).isFalse();
		assertThat(graph.allows("photolithography", "dry-etching", null)).isFalse();
		assertThat(graph.allows("nope", "wet-etching", null)).isFalse();
		assertThat(graph.allows("photolithography", "nope", null)).isFalse();
	}

	@Test
	void allowsOnlyTheListedWipLocationsOfARestrictedNextStage() {
		Stage photolithography = new Stage("photolithography", "Photolithography", 0,
				List.of("wet-etching", "dry-etching"), Map.of(),
				Map.of("wet-etching", List.of("WET-ETCHING-002"), "dry-etching", List.of()));
		StageGraph graph = new StageGraph(List.of(photolithography, wetEtching, dryEtching));

		assertThat(graph.allows("photolithography", "wet-etching", "WET-ETCHING-002")).isTrue();
		assertThat(graph.allows("photolithography", "wet-etching", "WET-ETCHING-001")).isFalse();
		assertThat(graph.allows("photolithography", "dry-etching", "DRY-ETCHING-001")).isFalse();
		assertThat(graph.allows("photolithography", "dry-etching", null)).isTrue();
	}

	@Test
	void allowedNextWipLocationsLeaveOutThoseTheNextStageNoLongerHas() {
		Stage photolithography = new Stage("photolithography", "Photolithography", 0, List.of("wet-etching"),
				Map.of(), Map.of("wet-etching", List.of("WET-ETCHING-001", "WET-ETCHING-009")));
		StageGraph graph = new StageGraph(List.of(photolithography, wetEtching));

		assertThat(graph.allowedNextWipLocations(photolithography))
				.containsExactly(Map.entry("wet-etching", List.of("WET-ETCHING-001")));
	}
}
