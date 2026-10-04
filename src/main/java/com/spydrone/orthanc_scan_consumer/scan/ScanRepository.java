package com.spydrone.orthanc_scan_consumer.scan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRepository extends JpaRepository<ScanEntity, String> {

	List<ScanEntity> findAllByOrderByReceivedAtDesc();
}
