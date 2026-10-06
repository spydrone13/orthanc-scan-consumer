package com.spydrone.orthanc_scan_consumer.lot.application;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;

/** Read-only projections of the lots table straight into {@link LotView}, without loading aggregates. */
interface LotViewRepository extends Repository<Lot, String> {

	String SELECT_VIEW = "select new com.spydrone.orthanc_scan_consumer.lot.application.LotView("
			+ "l.lotId, l.location.stage, l.location.wipLocation, l.status, l.onHold, l.updatedAt) from Lot l";

	@Query(SELECT_VIEW + " order by l.updatedAt desc")
	List<LotView> findAllNewestFirst();

	@Query(SELECT_VIEW + " where l.lotId = :lotId")
	Optional<LotView> findView(String lotId);

	boolean existsById(String lotId);
}
