package com.spydrone.orthanc_scan_consumer.lot.history;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LotStageEventRepository extends JpaRepository<LotStageEvent, String> {

	List<LotStageEvent> findByLotIdOrderByOccurredAtDesc(String lotId);

	/** Rows flagged for review, newest first. */
	List<LotStageEvent> findByExceptionIsNotNullAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(Instant since);
}
