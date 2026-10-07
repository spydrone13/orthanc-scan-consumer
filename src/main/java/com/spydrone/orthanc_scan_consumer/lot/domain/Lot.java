package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.spydrone.orthanc_scan_consumer.scan.ScanType;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Aggregate root for a lot: where it is, its status and whether it's on hold, and the rules for
 * scanning it. Changes only through its methods; scans are reported as {@link ScanApplied} or
 * {@link ScanRejected} domain events, published when the lot is saved.
 */
@Entity
@Table(name = "lots")
public class Lot extends AbstractAggregateRoot<Lot> {

	@Id
	private String lotId;
	/** Null when both columns are null (Hibernate's rule for embeddables); see {@link #location()}. */
	@Embedded
	private Location location;
	/**
	 * A {@link LotStatus} name, kept as a plain string so the column accepts statuses added later
	 * (see SchemaTest). The column default fills in lots stored before status existed.
	 */
	@Column(nullable = false)
	@ColumnDefault("'ACTIVE'")
	private String status = LotStatus.ACTIVE.name();
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean onHold;
	private Instant updatedAt;
	/** The scan that put the lot where it is; null for lots stored before this was kept. */
	private String lastScanClientId;

	protected Lot() {
	}

	private Lot(String lotId) {
		this.lotId = Objects.requireNonNull(lotId);
	}

	/** A lot seen for the first time: active, not on hold, not yet anywhere. */
	public static Lot firstScanned(String lotId) {
		return new Lot(lotId);
	}

	/**
	 * Applies a scan. The scan's own scanType isn't trusted; where the lot goes comes from the
	 * destination fields:
	 * <ul>
	 * <li>stage and WIP location: into that WIP location in that stage
	 * <li>stage only: into that stage, no WIP location
	 * <li>WIP location only: into that WIP location in the scan's stage
	 * <li>neither: the lot stays where it is (or, if new, goes to the scan's stage)
	 * </ul>
	 * The checks, in order:
	 * <ol>
	 * <li>A lot that isn't active can't be scanned at all.
	 * <li>The scan's stage is where the lot physically is. If the records have it at another stage,
	 * it's moved there first ({@link LocationCorrected}, flagged for review).
	 * <li>A held lot can't leave its stage.
	 * <li>A move to another stage that {@code routes} doesn't allow is applied but flagged
	 * {@link Discrepancy#OFF_ROUTE}.
	 * </ol>
	 */
	public void applyScan(Scan scan, StageRoutes routes, Instant at) {
		LotStatus current = getStatus();
		if (current != LotStatus.ACTIVE) {
			Location from = location();
			Location to = destination(scan, from);
			registerEvent(new ScanRejected(lotId, scan, scanType(scan, to), from, RejectionReason.forStatus(current), at));
			return;
		}
		correctLocation(scan, at);

		Location from = location();
		Location to = destination(scan, from);
		if (onHold && !Objects.equals(to.stage(), from.stage())) {
			registerEvent(new ScanRejected(lotId, scan, scanType(scan, to), from, RejectionReason.LOT_ON_HOLD, at));
			return;
		}
		Discrepancy discrepancy = isOffRoute(scan, to, routes) ? Discrepancy.OFF_ROUTE : null;
		this.location = to;
		this.lastScanClientId = scan.clientId();
		this.updatedAt = at;
		registerEvent(new ScanApplied(lotId, scan, scanType(scan, to), from, to, discrepancy, at));
	}

	/**
	 * Moves the lot to the scan's stage when the records have it at another one. Not when the
	 * records already have it at the scan's destination: that's the same move scanned again.
	 */
	private void correctLocation(Scan scan, Instant at) {
		Location recorded = location();
		String scanStage = blankToNull(scan.scanStage());
		if (recorded.stage() == null || scanStage == null || recorded.stage().equals(scanStage)
				|| recorded.stage().equals(blankToNull(scan.destinationStage()))) {
			return;
		}
		Location corrected = new Location(scanStage, null);
		Discrepancy discrepancy = blankToNull(scan.correctionReason()) != null
				? Discrepancy.LOCATION_CORRECTED
				: Discrepancy.LOCATION_MISMATCH_UNCONFIRMED;
		registerEvent(new LocationCorrected(lotId, scan, recorded, corrected, discrepancy, lastScanClientId, at));
		this.location = corrected;
		this.lastScanClientId = scan.clientId();
		this.updatedAt = at;
	}

	/** Moves within the scan's stage always follow the route; moves out of it must follow the stage graph. */
	private static boolean isOffRoute(Scan scan, Location to, StageRoutes routes) {
		String scanStage = blankToNull(scan.scanStage());
		if (scanStage == null || Objects.equals(to.stage(), scanStage)) {
			return false;
		}
		return !routes.allows(scanStage, to.stage(), to.wipLocation());
	}

	private static ScanType scanType(Scan scan, Location to) {
		return Objects.equals(to.stage(), scan.scanStage()) ? ScanType.INFORMATIONAL : ScanType.TRANSITIONAL;
	}

	/** Any status may change to any other. */
	public void changeStatus(LotStatus status, Instant at) {
		this.status = Objects.requireNonNull(status).name();
		this.updatedAt = at;
	}

	/** While held, the lot can't be scanned out of its current stage. */
	public void placeOnHold(Instant at) {
		this.onHold = true;
		this.updatedAt = at;
	}

	public void releaseHold(Instant at) {
		this.onHold = false;
		this.updatedAt = at;
	}

	private static Location destination(Scan scan, Location from) {
		String destinationStage = blankToNull(scan.destinationStage());
		String destinationWipLocation = blankToNull(scan.destinationWipLocation());
		if (destinationStage != null) {
			return new Location(destinationStage, destinationWipLocation);
		}
		if (destinationWipLocation != null || from.stage() == null) {
			return new Location(scan.scanStage(), destinationWipLocation);
		}
		return from;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public String getLotId() {
		return lotId;
	}

	public Location location() {
		return location == null ? Location.NOWHERE : location;
	}

	public LotStatus getStatus() {
		return LotStatus.valueOf(status);
	}

	public boolean isOnHold() {
		return onHold;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
