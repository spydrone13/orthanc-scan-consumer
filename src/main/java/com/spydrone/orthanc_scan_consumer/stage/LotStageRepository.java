package com.spydrone.orthanc_scan_consumer.stage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LotStageRepository extends JpaRepository<LotStageEntity, String> {

	List<LotStageEntity> findAllByOrderByPositionAsc();
}
