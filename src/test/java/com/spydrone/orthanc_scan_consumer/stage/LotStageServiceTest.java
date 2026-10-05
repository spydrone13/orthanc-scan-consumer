package com.spydrone.orthanc_scan_consumer.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void updatePersistsNextStagesAndWipLocations() {
		LotStage updated = service.update("photolithography",
				List.of("wet-etching", "dry-etching"),
				List.of("PHOTOLITHOGRAPHY-001", "  PHOTOLITHOGRAPHY-004 ", ""));

		assertThat(updated.description()).isEqualTo("Photolithography");
		assertThat(updated.nextStages()).containsExactly("wet-etching", "dry-etching");
		assertThat(updated.wipLocations()).containsExactly("PHOTOLITHOGRAPHY-001", "PHOTOLITHOGRAPHY-004");
		assertThat(service.getStages().get("photolithography")).isEqualTo(updated);
	}

	@Test
	void updateAllowsClearingBothLists() {
		LotStage updated = service.update("testing", List.of(), List.of());

		assertThat(updated.nextStages()).isEmpty();
		assertThat(updated.wipLocations()).isEmpty();
	}

	@Test
	void rejectsUnknownNextStage() {
		assertBadRequest(() -> service.update("intake", List.of("nope"), List.of()), "Unknown next stage: nope");
	}

	@Test
	void rejectsSelfAsNextStage() {
		assertBadRequest(() -> service.update("intake", List.of("intake"), List.of()), "own next stage");
	}

	@Test
	void rejectsDuplicateNextStages() {
		assertBadRequest(() -> service.update("intake", List.of("wafer-prep", "wafer-prep"), List.of()),
				"Duplicate next stage");
	}

	@Test
	void rejectsDuplicateWipLocationsIgnoringCase() {
		assertBadRequest(() -> service.update("intake", List.of(), List.of("INTAKE-001", "intake-001")),
				"Duplicate WIP location");
	}

	@Test
	void unknownStageIsNotFound() {
		assertThatThrownBy(() -> service.update("nope", List.of(), List.of()))
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
