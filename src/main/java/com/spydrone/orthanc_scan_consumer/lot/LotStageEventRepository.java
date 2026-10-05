package com.spydrone.orthanc_scan_consumer.lot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LotStageEventRepository extends JpaRepository<LotStageEvent, String> {

	List<LotStageEvent> findByLotIdOrderByOccurredAtDesc(String lotId);
}
