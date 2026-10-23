package com.spydrone.orthanc_scan_consumer.stage.adapter.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.spydrone.orthanc_scan_consumer.stage.domain.Stage;
import com.spydrone.orthanc_scan_consumer.stage.domain.StageRepository;
import com.spydrone.orthanc_scan_consumer.stage.domain.WipLocation;

/** Stores stages in the lot_stages tables through Spring Data JPA. */
@Repository
public class JpaStageRepository implements StageRepository {

	private final SpringDataLotStageRepository jpa;

	JpaStageRepository(SpringDataLotStageRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Stage> findAllInProcessOrder() {
		return jpa.findAllByOrderByPositionAsc().stream().map(JpaStageRepository::toStage).toList();
	}

	@Override
	public boolean isEmpty() {
		return jpa.count() == 0;
	}

	@Override
	@Transactional
	public void save(Stage stage) {
		LotStageEntity entity = jpa.findById(stage.id())
				.orElseGet(() -> new LotStageEntity(stage.id(), stage.description(), stage.position()));
		List<WipLocationEntry> wip = new ArrayList<>();
		stage.wipLocations().forEach((wipId, location) ->
				wip.add(new WipLocationEntry(wipId, location == null ? null : location.description())));
		List<NextWipLocationEntry> nextWip = new ArrayList<>();
		stage.nextWipLocations().forEach((nextStage, wipIds) ->
				wipIds.forEach(wipId -> nextWip.add(new NextWipLocationEntry(nextStage, wipId))));
		entity.replace(stage.nextStages(), wip, List.copyOf(stage.nextWipLocations().keySet()), nextWip);
		jpa.save(entity);
	}

	@Override
	@Transactional
	public void saveAll(List<Stage> stages) {
		stages.forEach(this::save);
	}

	private static Stage toStage(LotStageEntity entity) {
		Map<String, WipLocation> wip = new LinkedHashMap<>();
		for (WipLocationEntry entry : entity.getWipLocations()) {
			wip.put(entry.getId(), new WipLocation(entry.getDescription()));
		}
		Map<String, List<String>> nextWip = new LinkedHashMap<>();
		for (String nextStage : entity.getRestrictedNextStages()) {
			nextWip.put(nextStage, entity.getNextWipLocations().stream()
					.filter(entry -> entry.getNextStage().equals(nextStage))
					.map(NextWipLocationEntry::getWipLocation)
					.toList());
		}
		return new Stage(entity.getId(), entity.getDescription(), entity.getPosition(),
				entity.getNextStages(), wip, nextWip);
	}
}
