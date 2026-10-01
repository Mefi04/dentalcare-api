package com.dentalcare.api.modules.reports.repository;

public record PatientMetricsSnapshot(
        long total,
        long registeredInPeriod) {
}
