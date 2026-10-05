package com.spydrone.orthanc_scan_consumer.lot;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRepository extends JpaRepository<LotEntity, String> {

	List<LotEntity> findAllByOrderByUpdatedAtDesc();
}
