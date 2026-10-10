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
	/** S1 → S2 and S2 → S3, with any WIP location except WIP-X. */
	private static final StageRoutes ROUTES = (from, to, wip) ->
			(from.equals("S1") && to.equals("S2") || from.equals("S2") && to.equals("S3")) && !"WIP-X".equals(wip);

	@Test
	void firstScanCreatesActiveLotInDestination() {
		Lot lot = Lot.firstScanned("L1");

		lot.applyScan(scan("S1", "S2", null), ROUTES, NOW);

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

		lot.applyScan(scan("S1", "S2", "WIP-2"), ROUTES, NOW);

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

		lot.applyScan(scan("S1", "S2", null), ROUTES, NOW);

		assertThat(lot.location()).isEqualTo(new Location("S2", null));
		assertThat(((ScanApplied) lastEvent(lot)).scanType()).isEqualTo(ScanType.TRANSITIONAL);
	}

	@Test
	void wipLocationOnlySetsWipWithinScanStage() {
		Lot lot = lotAt("S1", null);

		lot.applyScan(scan("S1", null, "WIP-2"), ROUTES, NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-2"));
		assertThat(((ScanApplied) lastEvent(lot)).scanType()).isEqualTo(ScanType.INFORMATIONAL);
	}

	@Test
	void noDestinationLeavesLotWhereItIs() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S1", null, null), ROUTES, NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-1"));
		assertThat(((ScanApplied) lastEvent(lot)).to()).isEqualTo(new Location("S1", "WIP-1"));
	}

	@Test
	void noDestinationOnFirstScanPlacesLotInScanStage() {
		Lot lot = Lot.firstScanned("L1");

		lot.applyScan(scan("S1", null, null), ROUTES, NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", null));
	}

	@Test
	void blankDestinationsAreIgnoredAndValuesTrimmed() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S1", "  ", " WIP-2 "), ROUTES, NOW);

		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-2"));
	}

	@Test
	void moveToANextStageIsNotFlagged() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S1", "S2", "WIP-2"), ROUTES, NOW);

		assertThat(events(lot)).hasSize(2);
		assertThat(((ScanApplied) lastEvent(lot)).discrepancy()).isNull();
	}

	@Test
	void moveToAStageThatIsNotNextIsAppliedButFlagged() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S1", "S3", null), ROUTES, NOW);

		assertThat(lot.location()).isEqualTo(new Location("S3", null));
		assertThat(((ScanApplied) lastEvent(lot)).discrepancy()).isEqualTo(Discrepancy.OFF_ROUTE);
	}

	@Test
	void moveToAWipLocationNotAllowedInTheNextStageIsFlagged() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S1", "S2", "WIP-X"), ROUTES, NOW);

		assertThat(lot.location()).isEqualTo(new Location("S2", "WIP-X"));
		assertThat(((ScanApplied) lastEvent(lot)).discrepancy()).isEqualTo(Discrepancy.OFF_ROUTE);
	}

	@Test
	void moveWithinTheScanStageIsNeverOffRoute() {
		Lot lot = lotAt("S3", "WIP-1");

		lot.applyScan(scan("S3", null, "WIP-X"), ROUTES, NOW);

		assertThat(((ScanApplied) lastEvent(lot)).discrepancy()).isNull();
	}

	@Test
	void missedScanUpstreamIsCorrectedToTheScanStageAndFlagged() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S2", "S3", null), ROUTES, NOW);

		List<?> events = events(lot);
		LocationCorrected correction = (LocationCorrected) events.get(events.size() - 2);
		assertThat(correction.from()).isEqualTo(new Location("S1", "WIP-1"));
		assertThat(correction.to()).isEqualTo(new Location("S2", null));
		assertThat(correction.discrepancy()).isEqualTo(Discrepancy.LOCATION_MISMATCH_UNCONFIRMED);
		assertThat(correction.correctsClientId()).isEqualTo("setup");
		ScanApplied move = (ScanApplied) lastEvent(lot);
		assertThat(move.from()).isEqualTo(new Location("S2", null));
		assertThat(move.to()).isEqualTo(new Location("S3", null));
		assertThat(move.discrepancy()).isNull();
		assertThat(lot.location()).isEqualTo(new Location("S3", null));
	}

	@Test
	void confirmedCorrectionIsFlaggedAsCorrected() {
		Lot lot = lotAt("S5", null);

		lot.applyScan(new Scan("abc", "u", "S2", "S3", null, "n", "It was on the S2 rack", null), ROUTES, NOW);

		List<?> events = events(lot);
		assertThat(((LocationCorrected) events.get(events.size() - 2)).discrepancy())
				.isEqualTo(Discrepancy.LOCATION_CORRECTED);
	}

	@Test
	void correctionConfirmedWithoutAReasonIsFlaggedAsCorrected() {
		Lot lot = lotAt("S5", null);

		lot.applyScan(new Scan("abc", "u", "S2", "S3", null, "n", null, true), ROUTES, NOW);

		List<?> events = events(lot);
		assertThat(((LocationCorrected) events.get(events.size() - 2)).discrepancy())
				.isEqualTo(Discrepancy.LOCATION_CORRECTED);
	}

	@Test
	void correctedMoveIsCheckedFromTheScanStage() {
		Lot lot = lotAt("S5", null);

		lot.applyScan(scan("S1", "S3", null), ROUTES, NOW);

		assertThat(((ScanApplied) lastEvent(lot)).discrepancy()).isEqualTo(Discrepancy.OFF_ROUTE);
	}

	@Test
	void mismatchWithoutDestinationLeavesLotAtTheScanStage() {
		Lot lot = lotAt("S1", "WIP-1");

		lot.applyScan(scan("S5", null, "WIP-9"), ROUTES, NOW);

		assertThat(events(lot).get(events(lot).size() - 2)).isInstanceOf(LocationCorrected.class);
		assertThat(lot.location()).isEqualTo(new Location("S5", "WIP-9"));
	}

	@Test
	void sameMoveScannedAgainIsNotACorrection() {
		Lot lot = lotAt("S2", null);

		lot.applyScan(scan("S1", "S2", "WIP-2"), ROUTES, NOW);

		assertThat(events(lot)).noneMatch(LocationCorrected.class::isInstance);
		assertThat(lot.location()).isEqualTo(new Location("S2", "WIP-2"));
	}

	@Test
	void heldLotIsCorrectedButNotMovedOn() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.placeOnHold(EARLIER);

		lot.applyScan(scan("S2", "S3", null), ROUTES, NOW);

		List<?> events = events(lot);
		assertThat(events.get(events.size() - 2)).isInstanceOf(LocationCorrected.class);
		assertThat(((ScanRejected) lastEvent(lot)).reason()).isEqualTo(RejectionReason.LOT_ON_HOLD);
		assertThat(lot.location()).isEqualTo(new Location("S2", null));
	}

	@Test
	void inactiveLotIsNotCorrected() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.changeStatus(LotStatus.CANCELED, EARLIER);

		lot.applyScan(scan("S2", "S3", null), ROUTES, NOW);

		assertThat(events(lot)).noneMatch(LocationCorrected.class::isInstance);
		assertThat(lot.location()).isEqualTo(new Location("S1", "WIP-1"));
	}

	@Test
	void canceledLotIsNotMoved() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.changeStatus(LotStatus.CANCELED, EARLIER);

		lot.applyScan(scan("S1", "S2", null), ROUTES, NOW);

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

		lot.applyScan(scan("S1", "S1", "WIP-2"), ROUTES, NOW);

		assertThat(lot.location().wipLocation()).isEqualTo("WIP-1");
		assertThat(((ScanRejected) lastEvent(lot)).reason()).isEqualTo(RejectionReason.LOT_DESTROYED);
	}

	@Test
	void statusIsReportedBeforeHold() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.changeStatus(LotStatus.COMPLETE, EARLIER);
		lot.placeOnHold(EARLIER);

		lot.applyScan(scan("S1", "S2", null), ROUTES, NOW);

		assertThat(((ScanRejected) lastEvent(lot)).reason()).isEqualTo(RejectionReason.LOT_COMPLETE);
	}

	@Test
	void heldLotIsNotScannedOutOfItsStage() {
		Lot lot = lotAt("S1", "WIP-1");
		lot.placeOnHold(EARLIER);

		lot.applyScan(scan("S1", "S2", "WIP-2"), ROUTES, NOW);

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

		lot.applyScan(scan("S1", "S1", "WIP-2"), ROUTES, NOW);

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

	@Test
	void statusChangeIsReportedOnlyWhenTheStatusChanges() {
		Lot lot = lotAt("S1", "WIP-1");
		int before = events(lot).size();

		lot.changeStatus(LotStatus.ACTIVE, NOW);
		assertThat(events(lot)).hasSize(before);

		lot.changeStatus(LotStatus.COMPLETE, NOW);
		assertThat(lastEvent(lot)).isEqualTo(new LotStatusChanged("L1", LotStatus.ACTIVE, LotStatus.COMPLETE, NOW));
	}

	@Test
	void holdChangeIsReportedOnlyWhenTheHoldChanges() {
		Lot lot = lotAt("S1", "WIP-1");
		int before = events(lot).size();

		lot.releaseHold(NOW);
		assertThat(events(lot)).hasSize(before);

		lot.placeOnHold(NOW);
		lot.placeOnHold(NOW.plusSeconds(1));
		lot.releaseHold(NOW.plusSeconds(2));
		assertThat(List.<Object>copyOf(events(lot).subList(before, events(lot).size()))).containsExactly(
				new LotHoldChanged("L1", true, NOW),
				new LotHoldChanged("L1", false, NOW.plusSeconds(2)));
	}

	private static Scan scan(String stage, String destinationStage, String destinationWipLocation) {
		return new Scan("abc", "u", stage, destinationStage, destinationWipLocation, "n", null, null);
	}

	private static Lot lotAt(String stage, String wipLocation) {
		Lot lot = Lot.firstScanned("L1");
		lot.applyScan(new Scan("setup", "u", stage, stage, wipLocation, "", null, null), ROUTES, EARLIER);
		return lot;
	}

	/** AbstractAggregateRoot keeps registered events behind a protected accessor. */
	private static List<?> events(Lot lot) {
		Collection<?> events = ReflectionTestUtils.invokeMethod(lot, "domainEvents");
		return List.copyOf(events);
	}

	private static Object lastEvent(Lot lot) {
		List<?> list = events(lot);
		return list.get(list.size() - 1);
	}
}
