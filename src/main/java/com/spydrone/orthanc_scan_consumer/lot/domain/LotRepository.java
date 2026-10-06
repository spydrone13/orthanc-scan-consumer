package com.spydrone.orthanc_scan_consumer.lot.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** Loads and saves whole Lot aggregates; saving publishes the lot's domain events. */
public interface LotRepository extends JpaRepository<Lot, String> {
}
