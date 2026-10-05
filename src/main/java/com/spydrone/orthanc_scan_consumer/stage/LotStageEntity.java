package com.spydrone.orthanc_scan_consumer.stage;

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

@Entity
@Table(name = "lot_stages")
public class LotStageEntity {

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
	@Column(name = "wip_location")
	private List<String> wipLocations = new ArrayList<>();

	protected LotStageEntity() {
	}

	public LotStageEntity(String id, LotStage stage, int position) {
		this.id = id;
		this.description = stage.description();
		this.position = position;
		this.nextStages.addAll(stage.nextStages());
		this.wipLocations.addAll(stage.wipLocations());
	}

	public String getId() {
		return id;
	}

	public LotStage toLotStage() {
		return new LotStage(description, List.copyOf(nextStages), List.copyOf(wipLocations));
	}

	public void replace(List<String> nextStages, List<String> wipLocations) {
		this.nextStages.clear();
		this.nextStages.addAll(nextStages);
		this.wipLocations.clear();
		this.wipLocations.addAll(wipLocations);
	}
}
