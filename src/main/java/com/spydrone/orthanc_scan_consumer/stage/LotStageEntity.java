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

	/**
	 * Next stages that allow only some of their WIP locations (see {@link LotStage#nextWipLocations()}),
	 * kept apart from the allowed locations so a next stage can allow none.
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

	public LotStageEntity(String id, LotStage stage, int position) {
		this.id = id;
		this.description = stage.description();
		this.position = position;
		this.nextStages.addAll(stage.nextStages());
		replaceWipLocations(stage.wipLocations());
		replaceNextWipLocations(stage.nextWipLocations());
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
		Map<String, List<String>> nextWip = new LinkedHashMap<>();
		for (String nextStage : restrictedNextStages) {
			nextWip.put(nextStage, nextWipLocations.stream()
					.filter(entry -> entry.getNextStage().equals(nextStage))
					.map(NextWipLocationEntry::getWipLocation)
					.toList());
		}
		return new LotStage(description, List.copyOf(nextStages), wip, nextWip);
	}

	public void replace(List<String> nextStages, Map<String, WipLocation> wipLocations,
			Map<String, List<String>> nextWipLocations) {
		this.nextStages.clear();
		this.nextStages.addAll(nextStages);
		replaceWipLocations(wipLocations);
		replaceNextWipLocations(nextWipLocations);
	}

	private void replaceNextWipLocations(Map<String, List<String>> nextWipLocations) {
		this.restrictedNextStages.clear();
		this.nextWipLocations.clear();
		nextWipLocations.forEach((nextStage, wipIds) -> {
			this.restrictedNextStages.add(nextStage);
			wipIds.forEach(wipId -> this.nextWipLocations.add(new NextWipLocationEntry(nextStage, wipId)));
		});
	}

	private void replaceWipLocations(Map<String, WipLocation> wipLocations) {
		this.wipLocations.clear();
		wipLocations.forEach((wipId, wip) ->
				this.wipLocations.add(new WipLocationEntry(wipId, wip == null ? null : wip.description())));
	}
}
