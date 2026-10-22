package com.spydrone.orthanc_scan_consumer.week;

import java.time.LocalDate;
import java.util.Objects;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A production week: Saturday to Friday, numbered within its year by its Friday (see
 * {@link ProductionWeeks#fridaysOf(int)}). Created open.
 */
@Entity
@Table(name = "production_weeks", uniqueConstraints = @UniqueConstraint(name = "uk_production_weeks_year_week",
		columnNames = { "prod_year", "week_number" }))
public class ProductionWeek {

	/** The Friday the week ends on. */
	@Id
	private LocalDate endDate;
	/** YEAR is reserved in H2. */
	@Column(name = "prod_year", nullable = false)
	private int year;
	@Column(nullable = false)
	private int weekNumber;
	/** A {@link WeekStatus} name, kept as a plain string so the column accepts statuses added later (see SchemaTest). */
	@Column(nullable = false)
	@ColumnDefault("'OPEN'")
	private String status = WeekStatus.OPEN.name();

	protected ProductionWeek() {
	}

	public ProductionWeek(int year, int weekNumber, LocalDate endDate) {
		this.year = year;
		this.weekNumber = weekNumber;
		this.endDate = Objects.requireNonNull(endDate);
	}

	public void changeStatus(WeekStatus status) {
		this.status = Objects.requireNonNull(status).name();
	}

	public int getYear() {
		return year;
	}

	public int getWeekNumber() {
		return weekNumber;
	}

	public LocalDate getStartDate() {
		return endDate.minusDays(6);
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public WeekStatus getStatus() {
		return WeekStatus.valueOf(status);
	}
}
