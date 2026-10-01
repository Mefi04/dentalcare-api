package com.dentalcare.api.modules.reports.dto.response;

public record AppointmentMetricsResponse(
        long total,
        long scheduled,
        long completed,
        long cancelled) {
}
