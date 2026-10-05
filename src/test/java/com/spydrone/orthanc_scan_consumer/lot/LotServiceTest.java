package com.spydrone.orthanc_scan_consumer.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.spydrone.orthanc_scan_consumer.scan.ScanRecord;
import com.spydrone.orthanc_scan_consumer.scan.ScanType;
import com.spydrone.orthanc_scan_consumer.stage.LotStageRepository;

class LotServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

	private final LotRepository lotRepository = mock(LotRepository.class);
	private final LotStageEventRepository eventRepository = mock(LotStageEventRepository.class);
	private final LotStageRepository lotStageRepository = mock(LotStageRepository.class);
	private final LotService service = new LotService(lotRepository, eventRepository, lotStageRepository);

	@BeforeEach
	void stages() {
		given(lotStageRepository.existsById("S1")).willReturn(true);
		given(lotStageRepository.existsById("S2")).willReturn(true);
		given(lotStageRepository.existsById("S5")).willReturn(true);
	}

	@Test
	void createsLotOnFirstScan() {
		given(lotRepository.findById("L1")).willReturn(Optional.empty());

		LotEntity lot = service.apply(scan("S1", "S2", ScanType.TRANSITIONAL), NOW);

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
	void stageDestinationMovesStageAndClearsWipWhateverTheScanTypeSays() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));

		LotEntity lot = service.apply(scan("S1", "S2", ScanType.INFORMATIONAL), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S2");
		assertThat(lot.getWipLocation()).isNull();

		LotStageEvent event = savedEvent();
		assertThat(event.getClientId()).isEqualTo("abc");
		assertThat(event.getLotId()).isEqualTo("L1");
		assertThat(event.getScanType()).isEqualTo(ScanType.TRANSITIONAL);
		assertThat(event.getUserName()).isEqualTo("u");
		assertThat(event.getNote()).isEqualTo("n");
		assertThat(event.getFromStage()).isEqualTo("S1");
		assertThat(event.getFromWipLocation()).isEqualTo("WIP-1");
		assertThat(event.getToStage()).isEqualTo("S2");
		assertThat(event.getToWipLocation()).isNull();
		assertThat(event.getOccurredAt()).isEqualTo(NOW);
	}

	@Test
	void nonStageDestinationSetsWipWithinScanStageWhateverTheScanTypeSays() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", null)));

		LotEntity lot = service.apply(scan("S1", "WIP-2", ScanType.TRANSITIONAL), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S1");
		assertThat(lot.getWipLocation()).isEqualTo("WIP-2");

		LotStageEvent event = savedEvent();
		assertThat(event.getScanType()).isEqualTo(ScanType.INFORMATIONAL);
		assertThat(event.getFromWipLocation()).isNull();
		assertThat(event.getToStage()).isEqualTo("S1");
		assertThat(event.getToWipLocation()).isEqualTo("WIP-2");
	}

	@Test
	void nullDestinationIsAWipLocation() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));

		LotEntity lot = service.apply(scan("S1", null, ScanType.TRANSITIONAL), NOW);

		verify(lotStageRepository, never()).existsById(any());
		assertThat(lot.getCurrentStage()).isEqualTo("S1");
		assertThat(lot.getWipLocation()).isNull();
		assertThat(savedEvent().getScanType()).isEqualTo(ScanType.INFORMATIONAL);
	}

	@Test
	void scanFromAnotherStageIsStillApplied() {
		given(lotRepository.findById("L1")).willReturn(Optional.of(lotAt("S1", "WIP-1")));

		LotEntity lot = service.apply(scan("S5", "WIP-9", ScanType.INFORMATIONAL), NOW);

		assertThat(lot.getCurrentStage()).isEqualTo("S5");
		assertThat(lot.getWipLocation()).isEqualTo("WIP-9");
		assertThat(savedEvent().getFromStage()).isEqualTo("S1");
	}

	private static ScanRecord scan(String stage, String destination, ScanType type) {
		return new ScanRecord("abc", "u", stage, "L1", destination, type, "n");
	}

	private static LotEntity lotAt(String stage, String wipLocation) {
		LotEntity lot = new LotEntity("L1");
		lot.apply(ScanType.TRANSITIONAL, null, stage, NOW.minusSeconds(60));
		if (wipLocation != null) {
			lot.apply(ScanType.INFORMATIONAL, stage, wipLocation, NOW.minusSeconds(30));
		}
		return lot;
	}

	private LotStageEvent savedEvent() {
		ArgumentCaptor<LotStageEvent> saved = ArgumentCaptor.forClass(LotStageEvent.class);
		verify(eventRepository).save(saved.capture());
		return saved.getValue();
	}
}
