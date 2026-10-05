package com.spydrone.orthanc_scan_consumer.stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	private List<WipLocationEntry> wipLocations = new ArrayList<>();

	protected LotStageEntity() {
	}

	public LotStageEntity(String id, LotStage stage, int position) {
		this.id = id;
		this.description = stage.description();
		this.position = position;
		this.nextStages.addAll(stage.nextStages());
		replaceWipLocations(stage.wipLocations());
	}

	public String getId() {
		return id;
	}

	/** WIP locations without a stored description (e.g. from before descriptions existed) get the default. */
	public LotStage toLotStage() {
		Map<String, WipLocation> wip = new LinkedHashMap<>();
		for (WipLocationEntry entry : wipLocations) {
			String wipDescription = entry.getDescription() != null
					? entry.getDescription()
					: WipLocation.defaultDescription(description, entry.getId());
			wip.put(entry.getId(), new WipLocation(wipDescription));
		}
		return new LotStage(description, List.copyOf(nextStages), wip);
	}

	public void replace(List<String> nextStages, Map<String, WipLocation> wipLocations) {
		this.nextStages.clear();
		this.nextStages.addAll(nextStages);
		replaceWipLocations(wipLocations);
	}

	private void replaceWipLocations(Map<String, WipLocation> wipLocations) {
		this.wipLocations.clear();
		wipLocations.forEach((wipId, wip) ->
				this.wipLocations.add(new WipLocationEntry(wipId, wip == null ? null : wip.description())));
	}
}
