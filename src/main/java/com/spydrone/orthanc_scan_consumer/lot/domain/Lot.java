package com.spydrone.orthanc_scan_consumer.lot.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.spydrone.orthanc_scan_consumer.scan.ScanType;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
	/** The column default fills in lots stored before status existed. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@ColumnDefault("'ACTIVE'")
	private LotStatus status = LotStatus.ACTIVE;
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean onHold;
	private Instant updatedAt;

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
	 * A lot that isn't active can't be scanned at all, and a held lot can't leave its stage.
	 */
	public void applyScan(Scan scan, Instant at) {
		Location from = location();
		Location to = destination(scan, from);
		ScanType scanType = Objects.equals(to.stage(), scan.scanStage())
				? ScanType.INFORMATIONAL
				: ScanType.TRANSITIONAL;

		Optional<RejectionReason> rejection = rejection(from, to);
		if (rejection.isPresent()) {
			registerEvent(new ScanRejected(lotId, scan, scanType, from, rejection.get(), at));
			return;
		}
		this.location = to;
		this.updatedAt = at;
		registerEvent(new ScanApplied(lotId, scan, scanType, from, to, at));
	}

	/** Any status may change to any other. */
	public void changeStatus(LotStatus status, Instant at) {
		this.status = Objects.requireNonNull(status);
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

	/** Status first: a finished lot can't be scanned at all. Then hold: it can't leave its stage. */
	private Optional<RejectionReason> rejection(Location from, Location to) {
		if (status != LotStatus.ACTIVE) {
			return Optional.of(RejectionReason.forStatus(status));
		}
		if (onHold && !Objects.equals(to.stage(), from.stage())) {
			return Optional.of(RejectionReason.LOT_ON_HOLD);
		}
		return Optional.empty();
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
		return status;
	}

	public boolean isOnHold() {
		return onHold;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
