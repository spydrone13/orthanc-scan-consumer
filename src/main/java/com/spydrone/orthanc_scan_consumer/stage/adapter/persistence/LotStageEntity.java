package com.spydrone.orthanc_scan_consumer.stage.adapter.persistence;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** Table mapping for a stage; {@link JpaStageRepository} maps it to and from the domain. */
@Entity
@Table(name = "lot_stages")
class LotStageEntity {

	@Id
	private String id;
	private String description;
	/** Process order, from the order of stages in the seed file. */
	private int position;

	@ElementCollection
	@CollectionTable(name = "lot_stage_next_stages", joinColumns = @JoinColumn(name = "stage_id"))
	@OrderColumn(name = "idx")
	@Column(name = "next_stage")
	private List<String> nextStages = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "lot_stage_wip_locations", joinColumns = @JoinColumn(name = "stage_id"))
	@OrderColumn(name = "idx")
	private List<WipLocationEntry> wipLocations = new ArrayList<>();

	/**
	 * Next stages that allow only some of their WIP locations, kept apart from the allowed locations
	 * so a next stage can allow none.
	 */
	@ElementCollection
	@CollectionTable(name = "lot_stage_restricted_next_stages", joinColumns = @JoinColumn(name = "stage_id"))
	@OrderColumn(name = "idx")
	@Column(name = "next_stage")
	private List<String> restrictedNextStages = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "lot_stage_next_wip_locations", joinColumns = @JoinColumn(name = "stage_id"))
	@OrderColumn(name = "idx")
	private List<NextWipLocationEntry> nextWipLocations = new ArrayList<>();

	protected LotStageEntity() {
	}

	LotStageEntity(String id, String description, int position) {
		this.id = id;
		this.description = description;
		this.position = position;
	}

	String getId() {
		return id;
	}

	String getDescription() {
		return description;
	}

	int getPosition() {
		return position;
	}

	List<String> getNextStages() {
		return nextStages;
	}

	List<WipLocationEntry> getWipLocations() {
		return wipLocations;
	}

	List<String> getRestrictedNextStages() {
		return restrictedNextStages;
	}

	List<NextWipLocationEntry> getNextWipLocations() {
		return nextWipLocations;
	}

	/** Replaces the collections in place, so Hibernate keeps managing the same ones. */
	void replace(List<String> nextStages, List<WipLocationEntry> wipLocations, List<String> restrictedNextStages,
			List<NextWipLocationEntry> nextWipLocations) {
		this.nextStages.clear();
		this.nextStages.addAll(nextStages);
		this.wipLocations.clear();
		this.wipLocations.addAll(wipLocations);
		this.restrictedNextStages.clear();
		this.restrictedNextStages.addAll(restrictedNextStages);
		this.nextWipLocations.clear();
		this.nextWipLocations.addAll(nextWipLocations);
	}
}
