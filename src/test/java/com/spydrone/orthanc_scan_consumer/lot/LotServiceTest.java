package com.spydrone.orthanc_scan_consumer.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;

class LotServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

	private final LotRepository lotRepository = mock(LotRepository.class);
	private final LotStageEventRepository eventRepository = mock(LotStageEventRepository.class);
	private final LotService service = new LotService(lotRepository, eventRepository);

	@Test
	void createsLotOnFirstScan() {
		given(lotRepository.findById("L1")).willReturn(Optional.empty());

		LotEntity lot = service.apply(scan("S1", "S2", null), NOW);

		verify(lotRepository).save(lot);
		assertThat(lot.getLotId()).isEqualTo("L1");
		assertThat(lot.getCurrentStage()).isEqualTo("S2");
		assertThat(lot.getUpdatedAt()).isEqualTo(NOW);

		LotStageEvent event = savedEvent();
		assertThat(event.getFromStage()).isNull();
		assertThat(event.getFromWipLocation()).isNull();
		assertThat(event.getToStage()).isEqualTo("S2");
	}

	@Test
	void stageAndWipLocationMovesIntoWipLocationInNextStage() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));

		LotEntity lot = service.apply(scan("S1", "S2", "WIP-2"), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S2");
		assertThat(lot.getWipLocation()).isEqualTo("WIP-2");

		LotStageEvent event = savedEvent();
		assertThat(event.getClientId()).isEqualTo("abc");
		assertThat(event.getLotId()).isEqualTo("L1");
		assertThat(event.getScanType()).isEqualTo(ScanType.TRANSITIONAL);
		assertThat(event.getUserName()).isEqualTo("u");
		assertThat(event.getNote()).isEqualTo("n");
		assertThat(event.getFromStage()).isEqualTo("S1");
		assertThat(event.getFromWipLocation()).isEqualTo("WIP-1");
		assertThat(event.getToStage()).isEqualTo("S2");
		assertThat(event.getToWipLocation()).isEqualTo("WIP-2");
		assertThat(event.getOccurredAt()).isEqualTo(NOW);
	}

	@Test
	void stageOnlyMovesStageAndClearsWip() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));

		LotEntity lot = service.apply(scan("S1", "S2", null, ScanType.INFORMATIONAL), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S2");
		assertThat(lot.getWipLocation()).isNull();
		assertThat(savedEvent().getScanType()).isEqualTo(ScanType.TRANSITIONAL);
	}

	@Test
	void wipLocationOnlySetsWipWithinScanStage() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", null)));

		LotEntity lot = service.apply(scan("S1", null, "WIP-2", ScanType.TRANSITIONAL), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S1");
		assertThat(lot.getWipLocation()).isEqualTo("WIP-2");

		LotStageEvent event = savedEvent();
		assertThat(event.getScanType()).isEqualTo(ScanType.INFORMATIONAL);
		assertThat(event.getFromWipLocation()).isNull();
		assertThat(event.getToWipLocation()).isEqualTo("WIP-2");
	}

	@Test
	void noDestinationLeavesLotWhereItIs() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));

		LotEntity lot = service.apply(scan("S5", null, null), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S1");
		assertThat(lot.getWipLocation()).isEqualTo("WIP-1");

		LotStageEvent event = savedEvent();
		assertThat(event.getToStage()).isEqualTo("S1");
		assertThat(event.getToWipLocation()).isEqualTo("WIP-1");
	}

	@Test
	void noDestinationOnFirstScanPlacesLotInScanStage() {
		given(lotRepository.findById("L1")).willReturn(Optional.empty());

		LotEntity lot = service.apply(scan("S1", null, null), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S1");
		assertThat(lot.getWipLocation()).isNull();
	}

	@Test
	void blankDestinationsAreIgnoredAndValuesTrimmed() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));

		LotEntity lot = service.apply(scan("S1", "  ", " WIP-2 "), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S1");
		assertThat(lot.getWipLocation()).isEqualTo("WIP-2");
	}

	@Test
	void scanFromAnotherStageIsStillApplied() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));

		LotEntity lot = service.apply(scan("S5", null, "WIP-9"), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S5");
		assertThat(lot.getWipLocation()).isEqualTo("WIP-9");
		assertThat(savedEvent().getFromStage()).isEqualTo("S1");
	}

	@Test
	void newLotIsActive() {
		given(lotRepository.findById("L1")).willReturn(Optional.empty());

		LotEntity lot = service.apply(scan("S1", "S2", null), NOW);

		assertThat(lot.getStatus()).isEqualTo(LotStatus.ACTIVE);
	}

	@Test
	void scanStillMovesANonActiveLot() {
		LotEntity canceled = lotAt("S1", "WIP-1");
		canceled.setStatus(LotStatus.CANCELED, NOW.minusSeconds(10));
		given(lotRepository.findById("L1")).willReturn(Optional.of(canceled));

		LotEntity lot = service.apply(scan("S1", "S2", null), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S2");
		assertThat(lot.getStatus()).isEqualTo(LotStatus.CANCELED);
	}

	@Test
	void updateStatusChangesStatusAndUpdatedAt() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));
		given(lotRepository.save(any())).willAnswer(call -> call.getArgument(0));

		LotEntity lot = service.updateStatus("L1", LotStatus.DESTROYED, NOW);

		assertThat(lot.getStatus()).isEqualTo(LotStatus.DESTROYED);
		assertThat(lot.getUpdatedAt()).isEqualTo(NOW);
		assertThat(lot.getCurrentStage()).isEqualTo("S1");
	}

	@Test
	void updateStatusOfUnknownLotIsNotFound() {
		given(lotRepository.findById("nope")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateStatus("nope", LotStatus.COMPLETE, NOW))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void updateStatusRequiresAStatus() {
		assertThatThrownBy(() -> service.updateStatus("L1", null, NOW))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void heldLotIsNotScannedOutOfItsStage() {
		LotEntity held = lotAt("S1", "WIP-1");
		held.setOnHold(true, NOW.minusSeconds(10));
		given(lotRepository.findById("L1")).willReturn(Optional.of(held));

		LotEntity lot = service.apply(scan("S1", "S2", "WIP-2"), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S1");
		assertThat(lot.getWipLocation()).isEqualTo("WIP-1");
		assertThat(lot.getUpdatedAt()).isEqualTo(NOW.minusSeconds(10));
		verify(lotRepository, never()).save(any());

		LotStageEvent event = savedEvent();
		assertThat(event.getRejectedReason()).isEqualTo("LOT_ON_HOLD");
		assertThat(event.getScanType()).isEqualTo(ScanType.TRANSITIONAL);
		assertThat(event.getFromStage()).isEqualTo("S1");
		assertThat(event.getToStage()).isEqualTo("S1");
		assertThat(event.getToWipLocation()).isEqualTo("WIP-1");
	}

	@Test
	void heldLotCanMoveBetweenWipLocationsInItsStage() {
		LotEntity held = lotAt("S1", "WIP-1");
		held.setOnHold(true, NOW.minusSeconds(10));
		given(lotRepository.findById("L1")).willReturn(Optional.of(held));

		LotEntity lot = service.apply(scan("S1", "S1", "WIP-2"), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S1");
		assertThat(lot.getWipLocation()).isEqualTo("WIP-2");
		assertThat(savedEvent().getRejectedReason()).isNull();
	}

	@Test
	void updateHoldSetsFlagAndUpdatedAt() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));
		given(lotRepository.save(any())).willAnswer(call -> call.getArgument(0));

		LotEntity lot = service.updateHold("L1", true, NOW);

		assertThat(lot.isOnHold()).isTrue();
		assertThat(lot.getUpdatedAt()).isEqualTo(NOW);
		assertThat(service.updateHold("L1", false, NOW).isOnHold()).isFalse();
	}

	@Test
	void updateHoldOfUnknownLotIsNotFound() {
		given(lotRepository.findById("nope")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateHold("nope", true, NOW))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void updateHoldRequiresAValue() {
		assertThatThrownBy(() -> service.updateHold("L1", null, NOW))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
	}

	private static ScanRecord scan(String stage, String destinationStage, String destinationWipLocation) {
		return scan(stage, destinationStage, destinationWipLocation, ScanType.TRANSITIONAL);
	}

	private static ScanRecord scan(String stage, String destinationStage, String destinationWipLocation,
			ScanType type) {
		return new ScanRecord("abc", "u", stage, "L1", destinationStage, destinationWipLocation, type, "n");
	}

	private static LotEntity lotAt(String stage, String wipLocation) {
		LotEntity lot = new LotEntity("L1");
		lot.moveTo(stage, wipLocation, NOW.minusSeconds(60));
		return lot;
	}

	private LotStageEvent savedEvent() {
		ArgumentCaptor<LotStageEvent> saved = ArgumentCaptor.forClass(LotStageEvent.class);
		verify(eventRepository).save(saved.capture());
		return saved.getValue();
	}
}
