package com.spydrone.orthanc_scan_consumer.week;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;

/** Read-only sums of active lots' wafers by contract ship date and type, without loading aggregates. */
interface ScheduledWafersRepository extends Repository<Lot, String> {

	/** Lots with no wafer count add nothing to {@code wafers} but are still counted in {@code lots}. */
	@Query("select new com.spydrone.orthanc_scan_consumer.week.ScheduledLots("
			+ "l.contractShipDate, l.chipType, l.processType, count(l), coalesce(sum(l.waferCount), 0L))"
			+ " from Lot l where l.status = 'ACTIVE' and l.contractShipDate between :from and :to"
			+ " group by l.contractShipDate, l.chipType, l.processType")
	List<ScheduledLots> findActiveShippingBetween(LocalDate from, LocalDate to);
}
