package com.dentalcare.api.modules.reports.dto.response;

public record DashboardReportResponse(
        ReportPeriodResponse period,
        PatientMetricsResponse patients,
        AppointmentMetricsResponse appointments,
        BillingMetricsResponse billing) {
}
