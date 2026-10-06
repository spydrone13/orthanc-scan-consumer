package com.spydrone.orthanc_scan_consumer.lot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.spydrone.orthanc_scan_consumer.scan.ScanType;

/** The lot's scanning rules, tested on the aggregate alone. */
class LotTest {

	private static final Instant EARLIER = Instant.parse("2026-10-05T11:00:00Z");
	private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

	@Test
	void firstScanCreatesActiveLotInDestination() {
		Lot lot = Lot.firstScanned("L1");

		lot.applyScan(scan("S1", "S2", null), NOW);

		assertThat(lot.getLotId()).isEqualTo("L1");
		assertThat(lot.location()).isEqualTo(new Location("S2", null));
		assertThat(lot.getStatus()).isEqualTo(LotStatus.ACTIVE);
		assertThat(lot.isOnHold()).isFalse();
		assertThat(lot.getUpdatedAt()).isEqualTo(NOW);
		ScanApplied event = (ScanApplied) lastEvent(lot);
		assertThat(event.from()).isEqualTo(Location.NOWHERE);
		assertThat(event.to()).isEqualTo(new Location("S2", null));
	}

	@Test
	void stageAndWipLocationMovesIntoWipLocationInNextStage() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S1", "S2", "WIP-2"), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S2", "WIP-2"));
		ScanApplied event = (ScanApplied) lastEvent(lot);
		assertThat(event.lotId()).isEqualTo("L1");
		assertThat(event.scan().clientId()).isEqualTo("abc");
		assertThat(event.scanType()).isEqualTo(ScanType.TRANSITIONAL);
		assertThat(event.from()).isEqualTo(new Location("S1", "WIP-1"));
		assertThat(event.to()).isEqualTo(new Location("S2", "WIP-2"));
		assertThat(event.at()).isEqualTo(NOW);
	}

	@Test
	void stageOnlyMovesStageAndClearsWip() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S1", "S2", null), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S2", null));
		assertThat(((ScanApplied) lastEvent(lot)).scanType()).isEqualTo(ScanType.TRANSITIONAL);
	}

	@Test
	void wipLocationOnlySetsWipWithinScanStage() {
		Lot lot = lotAt("S1", null);

		lot.applyScan(scan("S1", null, "WIP-2"), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-2"));
		assertThat(((ScanApplied) lastEvent(lot)).scanType()).isEqualTo(ScanType.INFORMATIONAL);
	}

	@Test
	void noDestinationLeavesLotWhereItIs() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S5", null, null), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-1"));
		assertThat(((ScanApplied) lastEvent(lot)).to()).isEqualTo(new Location("S1", "WIP-1"));
	}

	@Test
	void noDestinationOnFirstScanPlacesLotInScanStage() {
		Lot lot = Lot.firstScanned("L1");

		lot.applyScan(scan("S1", null, null), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", null));
	}

	@Test
	void blankDestinationsAreIgnoredAndValuesTrimmed() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S1", "  ", " WIP-2 "), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-2"));
	}

	@Test
	void scanFromAnotherStageIsStillApplied() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S5", null, "WIP-9"), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S5", "WIP-9"));
	}

	@Test
	void canceledLotIsNotMoved() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.changeStatus(LotStatus.CANCELED, EARLIER);

		lot.applyScan(scan("S1", "S2", null), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-1"));
		assertThat(lot.getUpdatedAt()).isEqualTo(EARLIER);
		ScanRejected event = (ScanRejected) lastEvent(lot);
		assertThat(event.reason()).isEqualTo(RejectionReason.LOT_CANCELED);
		assertThat(event.location()).isEqualTo(new Location("S1", "WIP-1"));
		assertThat(event.scanType()).isEqualTo(ScanType.TRANSITIONAL);
	}

	@Test
	void destroyedLotIsRejectedEvenWithinItsStage() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.changeStatus(LotStatus.DESTROYED, EARLIER);

		lot.applyScan(scan("S1", "S1", "WIP-2"), NOW);

		assertThat(lot.location().wipLocation()).isEqualTo("WIP-1");
		assertThat(((ScanRejected) lastEvent(lot)).reason()).isEqualTo(RejectionReason.LOT_DESTROYED);
	}

	@Test
	void statusIsReportedBeforeHold() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.changeStatus(LotStatus.COMPLETE, EARLIER);
		lot.placeOnHold(EARLIER);

		lot.applyScan(scan("S1", "S2", null), NOW);

		assertThat(((ScanRejected) lastEvent(lot)).reason()).isEqualTo(RejectionReason.LOT_COMPLETE);
	}

	@Test
	void heldLotIsNotScannedOutOfItsStage() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.placeOnHold(EARLIER);

		lot.applyScan(scan("S1", "S2", "WIP-2"), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-1"));
		assertThat(lot.getUpdatedAt()).isEqualTo(EARLIER);
		ScanRejected event = (ScanRejected) lastEvent(lot);
		assertThat(event.reason()).isEqualTo(RejectionReason.LOT_ON_HOLD);
		assertThat(event.scanType()).isEqualTo(ScanType.TRANSITIONAL);
	}

	@Test
	void heldLotCanMoveBetweenWipLocationsInItsStage() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.placeOnHold(EARLIER);

		lot.applyScan(scan("S1", "S1", "WIP-2"), NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-2"));
		assertThat(lastEvent(lot)).isInstanceOf(ScanApplied.class);
	}

	@Test
	void changeStatusAndHoldUpdateTimestamp() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.changeStatus(LotStatus.DESTROYED, NOW);
		assertThat(lot.getStatus()).isEqualTo(LotStatus.DESTROYED);
		assertThat(lot.getUpdatedAt()).isEqualTo(NOW);

		lot.placeOnHold(NOW.plusSeconds(1));
		assertThat(lot.isOnHold()).isTrue();
		lot.releaseHold(NOW.plusSeconds(2));
		assertThat(lot.isOnHold()).isFalse();
		assertThat(lot.getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-1"));
	}

	private static Scan scan(String stage, String destinationStage, String destinationWipLocation) {
		return new Scan("abc", "u", stage, destinationStage, destinationWipLocation, "n");
	}

	private static Lot lotAt(String stage, String wipLocation) {
		Lot lot = Lot.firstScanned("L1");
		lot.applyScan(new Scan("setup", "u", stage, stage, wipLocation, ""), EARLIER);
		return lot;
	}

	/** AbstractAggregateRoot keeps registered events behind a protected accessor. */
	private static Object lastEvent(Lot lot) {
		Collection<?> events = ReflectionTestUtils.invokeMethod(lot, "domainEvents");
		List<?> list = List.copyOf(events);
		return list.get(list.size() - 1);
	}
}
