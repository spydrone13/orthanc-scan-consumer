package com.spydrone.orthanc_scan_consumer.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.spydrone.orthanc_scan_consumer.lot.LotService;

class ScanListenerTest {

	private static final ScanRecord RECORD =
			new ScanRecord("abc", "u", "S1", "L1", "S2", ScanType.TRANSITIONAL, "");

	private final ScanRepository repository = mock(ScanRepository.class);
	private final LotService lotService = mock(LotService.class);
	private final ScanListener listener = new ScanListener(repository, lotService);

	@Test
	void savesNewScanAndAppliesItToTheLot() {
		listener.onScan(RECORD);

		ArgumentCaptor<ScanEntity> saved = ArgumentCaptor.forClass(ScanEntity.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getClientId()).isEqualTo("abc");
		assertThat(saved.getValue().getScanType()).isEqualTo(ScanType.TRANSITIONAL);
		assertThat(saved.getValue().getReceivedAt()).isNotNull();
		verify(lotService).apply(RECORD, saved.getValue().getReceivedAt());
	}

	@Test
	void skipsDuplicateScan() {
		given(repository.existsById("abc")).willReturn(true);

		listener.onScan(RECORD);

		verify(repository, never()).save(any());
		verify(lotService, never()).apply(any(), any());
	}
}
