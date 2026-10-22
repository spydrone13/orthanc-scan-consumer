package com.spydrone.orthanc_scan_consumer.week;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionWeekRepository extends JpaRepository<ProductionWeek, LocalDate> {

	List<ProductionWeek> findByYearOrderByWeekNumber(int year);

	Optional<ProductionWeek> findByYearAndWeekNumber(int year, int weekNumber);

	List<ProductionWeek> findAllByOrderByEndDate();
}
