package com.dentalcare.api.modules.reports.dto.response;

public record PatientMetricsResponse(
        long total,
        long registeredInPeriod) {
}
