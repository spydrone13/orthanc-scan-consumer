package com.spydrone.orthanc_scan_consumer.lot.events;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	/** Unpublished events, oldest first. */
	List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

	@Transactional
	@Modifying
	@Query("delete from OutboxEvent e where e.publishedAt < :before")
	int deletePublishedBefore(Instant before);
}
