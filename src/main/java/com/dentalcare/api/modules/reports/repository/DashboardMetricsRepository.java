package com.dentalcare.api.modules.reports.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentStatus;

import java.time.Instant;
import java.util.Map;

public interface DashboardMetricsRepository {

    PatientMetricsSnapshot findPatientMetrics(Instant fromInclusive, Instant toExclusive);

    Map<AppointmentStatus, Long> countAppointmentsByStatus(Instant fromInclusive, Instant toExclusive);

    BillingMetricsSnapshot findBillingMetrics(Instant fromInclusive, Instant toExclusive);
}
