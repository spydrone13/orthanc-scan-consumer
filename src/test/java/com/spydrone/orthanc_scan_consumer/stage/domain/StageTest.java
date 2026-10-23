package com.spydrone.orthanc_scan_consumer.stage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StageTest {

	private Stage intake;
	private Stage photolithography;
	private StageGraph graph;

	@BeforeEach
	void setUp() {
		intake = stage("intake", "Intake", List.of("wafer-prep"), "INTAKE-001", "INTAKE-002");
		photolithography = stage("photolithography", "Photolithography", List.of("wet-etching"));
		graph = new StageGraph(List.of(intake, stage("wafer-prep", "Wafer Prep", List.of()), photolithography,
				stage("wet-etching", "Wet Etching", List.of(), "WET-ETCHING-001", "WET-ETCHING-002", "WET-ETCHING-003"),
				stage("dry-etching", "Dry Etching", List.of(), "DRY-ETCHING-001"),
				stage("deposition", "Deposition", List.of(), "DEPOSITION-001")));
	}

	@Test
	void changeRoutesTrimsWipLocationsAndDropsBlankIds() {
		photolithography.changeRoutes(List.of("wet-etching", "dry-etching"),
				wip("PHOTOLITHOGRAPHY-004", " Litho Bay 4 ", "  PHOTOLITHOGRAPHY-001 ", "Litho Bay 1", "", "dropped"),
				null, graph);

		assertThat(photolithography.nextStages()).containsExactly("wet-etching", "dry-etching");
		assertThat(photolithography.wipLocations()).containsExactly(
				Map.entry("PHOTOLITHOGRAPHY-004", new WipLocation("Litho Bay 4")),
				Map.entry("PHOTOLITHOGRAPHY-001", new WipLocation("Litho Bay 1")));
	}

	@Test
	void blankOrMissingWipDescriptionIsStoredAsNullAndDescribedByDefault() {
		Map<String, WipLocation> input = wip("INTAKE-007", "  ", "BIN", null);
		input.put("INTAKE-008", null);

		intake.changeRoutes(List.of("wafer-prep"), input, null, graph);

		assertThat(intake.wipLocations()).containsExactly(
				Map.entry("INTAKE-007", new WipLocation(null)),
				Map.entry("BIN", new WipLocation(null)),
				Map.entry("INTAKE-008", new WipLocation(null)));
		assertThat(intake.describedWipLocations()).containsExactly(
				Map.entry("INTAKE-007", new WipLocation("Intake 7")),
				Map.entry("BIN", new WipLocation("BIN")),
				Map.entry("INTAKE-008", new WipLocation("Intake 8")));
	}

	@Test
	void changeRoutesAllowsClearingBothLists() {
		intake.changeRoutes(List.of(), Map.of(), null, graph);

		assertThat(intake.nextStages()).isEmpty();
		assertThat(intake.wipLocations()).isEmpty();
	}

	@Test
	void allowedNextStageWipLocationsAreKeptInNextStageOrder() {
		Map<String, List<String>> nextWip = new LinkedHashMap<>();
		nextWip.put("deposition", List.of());
		nextWip.put("wet-etching", List.of("WET-ETCHING-003", "WET-ETCHING-001"));

		photolithography.changeRoutes(List.of("wet-etching", "dry-etching", "deposition"), Map.of(), nextWip, graph);

		assertThat(photolithography.nextWipLocations()).containsExactly(
				Map.entry("wet-etching", List.of("WET-ETCHING-001", "WET-ETCHING-003")),
				Map.entry("deposition", List.of()));
	}

	@Test
	void rejectsUnknownNextStage() {
		assertInvalid(() -> intake.changeRoutes(List.of("nope"), Map.of(), null, graph), "Unknown next stage: nope");
	}

	@Test
	void rejectsSelfAsNextStage() {
		assertInvalid(() -> intake.changeRoutes(List.of("intake"), Map.of(), null, graph), "own next stage");
	}

	@Test
	void rejectsDuplicateNextStages() {
		assertInvalid(() -> intake.changeRoutes(List.of("wafer-prep", "wafer-prep"), Map.of(), null, graph),
				"Duplicate next stage");
	}

	@Test
	void rejectsDuplicateWipLocationsIgnoringCase() {
		assertInvalid(() -> intake.changeRoutes(List.of(), wip("INTAKE-001", "a", "intake-001", "b"), null, graph),
				"Duplicate WIP location");
	}

	@Test
	void rejectsWipLocationsThatAreDuplicatesOnceTrimmed() {
		assertInvalid(() -> intake.changeRoutes(List.of(), wip("INTAKE-001", "a", " INTAKE-001 ", "b"), null, graph),
				"Duplicate WIP location");
	}

	@Test
	void rejectsAllowedWipLocationsForAStageThatIsNotNext() {
		assertInvalid(() -> photolithography.changeRoutes(List.of("wet-etching"), Map.of(),
				Map.of("dry-etching", List.of("DRY-ETCHING-001")), graph), "dry-etching is not a next stage");
	}

	@Test
	void rejectsAllowedWipLocationNotInTheNextStage() {
		assertInvalid(() -> photolithography.changeRoutes(List.of("wet-etching"), Map.of(),
				Map.of("wet-etching", List.of("DRY-ETCHING-001")), graph),
				"Unknown WIP location DRY-ETCHING-001 in stage wet-etching");
	}

	@Test
	void rejectsDuplicateAllowedWipLocations() {
		assertInvalid(() -> photolithography.changeRoutes(List.of("wet-etching"), Map.of(),
				Map.of("wet-etching", List.of("WET-ETCHING-001", "WET-ETCHING-001")), graph),
				"Duplicate WIP location for wet-etching");
	}

	@Test
	void rejectedChangeLeavesTheStageAsItWas() {
		assertThatThrownBy(() -> intake.changeRoutes(List.of("nope"), Map.of(), null, graph))
				.isInstanceOf(InvalidStageChangeException.class);

		assertThat(intake.nextStages()).containsExactly("wafer-prep");
		assertThat(intake.wipLocations()).containsOnlyKeys("INTAKE-001", "INTAKE-002");
	}

	static Stage stage(String id, String description, List<String> nextStages, String... wipIds) {
		Map<String, WipLocation> wip = new LinkedHashMap<>();
		for (String wipId : wipIds) {
			wip.put(wipId, new WipLocation(null));
		}
		return new Stage(id, description, 0, nextStages, wip, Map.of());
	}

	private static Map<String, WipLocation> wip(String... idsAndDescriptions) {
		Map<String, WipLocation> wip = new LinkedHashMap<>();
		for (int i = 0; i < idsAndDescriptions.length; i += 2) {
			wip.put(idsAndDescriptions[i], new WipLocation(idsAndDescriptions[i + 1]));
		}
		return wip;
	}

	private static void assertInvalid(Runnable call, String messagePart) {
		assertThatThrownBy(call::run)
				.isInstanceOf(InvalidStageChangeException.class)
				.hasMessageContaining(messagePart);
	}
}
