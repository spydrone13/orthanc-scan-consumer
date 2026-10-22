package com.spydrone.orthanc_scan_consumer.week;

/** Body of PUT /api/production-weeks/{year}/{week}/status. */
public record ProductionWeekStatusUpdate(WeekStatus status) {
}
