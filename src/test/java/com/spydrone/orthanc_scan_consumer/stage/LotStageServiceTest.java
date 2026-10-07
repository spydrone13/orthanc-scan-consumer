package com.spydrone.orthanc_scan_consumer.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest
@Import(LotStageService.class)
class LotStageServiceTest {

	@Autowired
	private LotStageService service;

	@Test
	void seedsStagesFromJsonInFileOrder() {
		Map<String, LotStage> stages = service.getStages();

		assertThat(stages).hasSize(14);
		assertThat(stages.keySet()).first().isEqualTo("intake");
		assertThat(stages.keySet()).last().isEqualTo("shipped");
		assertThat(stages.get("photolithography").nextStages())
				.containsExactly("wet-etching", "dry-etching", "deposition");
		assertThat(stages.get("shipped").nextStages()).isEmpty();
	}

	@Test
	void seedsWipLocationsWithDescriptionsInFileOrder() {
		Map<String, WipLocation> wip = service.getStages().get("wafer-prep").wipLocations();

		assertThat(wip.keySet()).containsExactly("WAFER-PREP-001", "WAFER-PREP-002", "WAFER-PREP-003");
		assertThat(wip.get("WAFER-PREP-001")).isEqualTo(new WipLocation("Wafer Prep 1"));
	}

	@Test
	void updatePersistsNextStagesAndWipLocations() {
		LotStage updated = service.update("photolithography",
				List.of("wet-etching", "dry-etching"),
				wip("PHOTOLITHOGRAPHY-004", " Litho Bay 4 ", "  PHOTOLITHOGRAPHY-001 ", "Litho Bay 1", "", "dropped"), null);

		assertThat(updated.description()).isEqualTo("Photolithography");
		assertThat(updated.nextStages()).containsExactly("wet-etching", "dry-etching");
		assertThat(updated.wipLocations()).containsExactly(
				Map.entry("PHOTOLITHOGRAPHY-004", new WipLocation("Litho Bay 4")),
				Map.entry("PHOTOLITHOGRAPHY-001", new WipLocation("Litho Bay 1")));
		assertThat(service.getStages().get("photolithography")).isEqualTo(updated);
	}

	@Test
	void blankOrMissingWipDescriptionShowsTheDefault() {
		Map<String, WipLocation> input = wip("INTAKE-007", "  ", "BIN", null);
		input.put("INTAKE-008", null);

		LotStage updated = service.update("intake", List.of("wafer-prep"), input, null);

		assertThat(updated.wipLocations()).containsExactly(
				Map.entry("INTAKE-007", new WipLocation("Intake 7")),
				Map.entry("BIN", new WipLocation("BIN")),
				Map.entry("INTAKE-008", new WipLocation("Intake 8")));
		assertThat(service.getStages().get("intake")).isEqualTo(updated);
	}

	@Test
	void updateAllowsClearingBothLists() {
		LotStage updated = service.update("testing", List.of(), Map.of(), null);

		assertThat(updated.nextStages()).isEmpty();
		assertThat(updated.wipLocations()).isEmpty();
	}

	@Test
	void rejectsUnknownNextStage() {
		assertBadRequest(() -> service.update("intake", List.of("nope"), Map.of(), null), "Unknown next stage: nope");
	}

	@Test
	void rejectsSelfAsNextStage() {
		assertBadRequest(() -> service.update("intake", List.of("intake"), Map.of(), null), "own next stage");
	}

	@Test
	void rejectsDuplicateNextStages() {
		assertBadRequest(() -> service.update("intake", List.of("wafer-prep", "wafer-prep"), Map.of(), null),
				"Duplicate next stage");
	}

	@Test
	void rejectsDuplicateWipLocationsIgnoringCase() {
		assertBadRequest(() -> service.update("intake", List.of(), wip("INTAKE-001", "a", "intake-001", "b"), null),
				"Duplicate WIP location");
	}

	@Test
	void rejectsWipLocationsThatAreDuplicatesOnceTrimmed() {
		assertBadRequest(() -> service.update("intake", List.of(), wip("INTAKE-001", "a", " INTAKE-001 ", "b"), null),
				"Duplicate WIP location");
	}

	@Test
	void seededStagesAllowAnyNextStageWipLocation() {
		assertThat(service.getStages().values()).allSatisfy(stage -> assertThat(stage.nextWipLocations()).isEmpty());
	}

	@Test
	void updatePersistsAllowedNextStageWipLocationsInNextStageOrder() {
		Map<String, List<String>> nextWip = new LinkedHashMap<>();
		nextWip.put("deposition", List.of());
		nextWip.put("wet-etching", List.of("WET-ETCHING-003", "WET-ETCHING-001"));

		LotStage updated = service.update("photolithography", List.of("wet-etching", "dry-etching", "deposition"),
				Map.of(), nextWip);

		assertThat(updated.nextWipLocations()).containsExactly(
				Map.entry("wet-etching", List.of("WET-ETCHING-001", "WET-ETCHING-003")),
				Map.entry("deposition", List.of()));
		assertThat(service.getStages().get("photolithography")).isEqualTo(updated);
	}

	@Test
	void allowedNextStageWipLocationsRemovedFromTheNextStageAreLeftOut() {
		service.update("photolithography", List.of("wet-etching"), Map.of(),
				Map.of("wet-etching", List.of("WET-ETCHING-001", "WET-ETCHING-002")));

		service.update("wet-etching", List.of("photoresist-removal"), wip("WET-ETCHING-002", "Bay 2"), null);

		assertThat(service.getStages().get("photolithography").nextWipLocations())
				.containsExactly(Map.entry("wet-etching", List.of("WET-ETCHING-002")));
	}

	@Test
	void rejectsAllowedWipLocationsForAStageThatIsNotNext() {
		assertBadRequest(() -> service.update("photolithography", List.of("wet-etching"), Map.of(),
				Map.of("dry-etching", List.of("DRY-ETCHING-001"))), "dry-etching is not a next stage");
	}

	@Test
	void rejectsAllowedWipLocationNotInTheNextStage() {
		assertBadRequest(() -> service.update("photolithography", List.of("wet-etching"), Map.of(),
				Map.of("wet-etching", List.of("DRY-ETCHING-001"))),
				"Unknown WIP location DRY-ETCHING-001 in stage wet-etching");
	}

	@Test
	void rejectsDuplicateAllowedWipLocations() {
		assertBadRequest(() -> service.update("photolithography", List.of("wet-etching"), Map.of(),
				Map.of("wet-etching", List.of("WET-ETCHING-001", "WET-ETCHING-001"))),
				"Duplicate WIP location for wet-etching");
	}

	private static Map<String, WipLocation> wip(String... idsAndDescriptions) {
		Map<String, WipLocation> wip = new LinkedHashMap<>();
		for (int i = 0; i < idsAndDescriptions.length; i += 2) {
			wip.put(idsAndDescriptions[i], new WipLocation(idsAndDescriptions[i + 1]));
		}
		return wip;
	}

	@Test
	void unknownStageIsNotFound() {
		assertThatThrownBy(() -> service.update("nope", List.of(), Map.of(), null))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	private static void assertBadRequest(Runnable call, String messagePart) {
		assertThatThrownBy(call::run)
				.isInstanceOfSatisfying(ResponseStatusException.class, e -> {
					assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(e.getReason()).contains(messagePart);
				});
	}
}
