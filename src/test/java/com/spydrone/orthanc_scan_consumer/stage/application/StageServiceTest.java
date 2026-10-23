package com.spydrone.orthanc_scan_consumer.stage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.spydrone.orthanc_scan_consumer.stage.adapter.persistence.JpaStageRepository;
import com.spydrone.orthanc_scan_consumer.stage.adapter.seed.ClasspathJsonStageSeedSource;
import com.spydrone.orthanc_scan_consumer.stage.domain.InvalidStageChangeException;
import com.spydrone.orthanc_scan_consumer.stage.domain.Stage;
import com.spydrone.orthanc_scan_consumer.stage.domain.StageGraph;
import com.spydrone.orthanc_scan_consumer.stage.domain.WipLocation;

/** The service with the real persistence and seed adapters; the stage rules are covered by StageTest. */
@DataJpaTest
@Import({ StageService.class, JpaStageRepository.class, ClasspathJsonStageSeedSource.class })
class StageServiceTest {

	@Autowired
	private StageService service;

	@Test
	void seedsStagesFromJsonInFileOrder() {
		StageGraph graph = service.stages();

		assertThat(graph.stages()).hasSize(14);
		assertThat(graph.stages()).first().extracting(Stage::id).isEqualTo("intake");
		assertThat(graph.stages()).last().extracting(Stage::id).isEqualTo("shipped");
		assertThat(stored("photolithography").nextStages())
				.containsExactly("wet-etching", "dry-etching", "deposition");
		assertThat(stored("shipped").nextStages()).isEmpty();
	}

	@Test
	void seedsWipLocationsWithDescriptionsInFileOrder() {
		Map<String, WipLocation> wip = stored("wafer-prep").wipLocations();

		assertThat(wip.keySet()).containsExactly("WAFER-PREP-001", "WAFER-PREP-002", "WAFER-PREP-003");
		assertThat(wip.get("WAFER-PREP-001")).isEqualTo(new WipLocation("Wafer Prep 1"));
	}

	@Test
	void seededStagesAllowAnyNextStageWipLocation() {
		assertThat(service.stages().stages()).allSatisfy(stage -> assertThat(stage.nextWipLocations()).isEmpty());
	}

	@Test
	void updatePersistsNextStagesAndWipLocations() {
		Stage updated = update("photolithography", List.of("wet-etching", "dry-etching"),
				wip("PHOTOLITHOGRAPHY-004", " Litho Bay 4 ", "PHOTOLITHOGRAPHY-001", "  "), null);

		assertThat(updated.description()).isEqualTo("Photolithography");
		assertThat(updated.nextStages()).containsExactly("wet-etching", "dry-etching");
		Stage stored = stored("photolithography");
		assertThat(stored.nextStages()).containsExactly("wet-etching", "dry-etching");
		assertThat(stored.wipLocations()).containsExactly(
				Map.entry("PHOTOLITHOGRAPHY-004", new WipLocation("Litho Bay 4")),
				Map.entry("PHOTOLITHOGRAPHY-001", new WipLocation(null)));
		assertThat(stored.describedWipLocations().get("PHOTOLITHOGRAPHY-001"))
				.isEqualTo(new WipLocation("Photolithography 1"));
		assertThat(stored.position()).isEqualTo(updated.position());
	}

	@Test
	void updatePersistsAllowedNextStageWipLocations() {
		Map<String, List<String>> nextWip = new LinkedHashMap<>();
		nextWip.put("deposition", List.of());
		nextWip.put("wet-etching", List.of("WET-ETCHING-003", "WET-ETCHING-001"));

		update("photolithography", List.of("wet-etching", "dry-etching", "deposition"), Map.of(), nextWip);

		assertThat(stored("photolithography").nextWipLocations()).containsExactly(
				Map.entry("wet-etching", List.of("WET-ETCHING-001", "WET-ETCHING-003")),
				Map.entry("deposition", List.of()));
	}

	@Test
	void allowedNextStageWipLocationsRemovedFromTheNextStageAreLeftOut() {
		update("photolithography", List.of("wet-etching"), Map.of(),
				Map.of("wet-etching", List.of("WET-ETCHING-001", "WET-ETCHING-002")));

		update("wet-etching", List.of("photoresist-removal"), wip("WET-ETCHING-002", "Bay 2"), null);

		StageGraph graph = service.stages();
		assertThat(graph.allowedNextWipLocations(graph.find("photolithography").orElseThrow()))
				.containsExactly(Map.entry("wet-etching", List.of("WET-ETCHING-002")));
	}

	@Test
	void invalidUpdateIsNotStored() {
		assertThatThrownBy(() -> update("intake", List.of("nope"), Map.of(), null))
				.isInstanceOf(InvalidStageChangeException.class)
				.hasMessage("Unknown next stage: nope");

		assertThat(stored("intake").nextStages()).containsExactly("wafer-prep");
	}

	@Test
	void unknownStageIsNotFound() {
		assertThatThrownBy(() -> update("nope", List.of(), Map.of(), null))
				.isInstanceOfSatisfying(StageNotFoundException.class,
						e -> assertThat(e.getStageId()).isEqualTo("nope"));
	}

	private Stage update(String id, List<String> nextStages, Map<String, WipLocation> wipLocations,
			Map<String, List<String>> nextWipLocations) {
		return service.updateRoutes(new UpdateStageRoutes(id, nextStages, wipLocations, nextWipLocations));
	}

	private Stage stored(String id) {
		return service.stages().find(id).orElseThrow();
	}

	private static Map<String, WipLocation> wip(String... idsAndDescriptions) {
		Map<String, WipLocation> wip = new LinkedHashMap<>();
		for (int i = 0; i < idsAndDescriptions.length; i += 2) {
			wip.put(idsAndDescriptions[i], new WipLocation(idsAndDescriptions[i + 1]));
		}
		return wip;
	}
}
