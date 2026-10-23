package com.spydrone.orthanc_scan_consumer.stage.adapter.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataLotStageRepository extends JpaRepository<LotStageEntity, String> {

	List<LotStageEntity> findAllByOrderByPositionAsc();
}
