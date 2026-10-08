package com.dentalcare.api.modules.reports.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.reports.dto.response.AppointmentMetricsResponse;
import com.dentalcare.api.modules.reports.dto.response.BillingMetricsResponse;
import com.dentalcare.api.modules.reports.dto.response.DashboardReportResponse;
import com.dentalcare.api.modules.reports.dto.response.PatientMetricsResponse;
import com.dentalcare.api.modules.reports.dto.response.ReportPeriodResponse;
import com.dentalcare.api.modules.reports.repository.BillingMetricsSnapshot;
import com.dentalcare.api.modules.reports.repository.DashboardMetricsRepository;
import com.dentalcare.api.modules.reports.repository.PatientMetricsSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Service
public class DashboardReportServiceImpl implements DashboardReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("America/Guatemala");

    private final DashboardMetricsRepository dashboardMetricsRepository;
    private final Clock clock;
    private final java.util.concurrent.Semaphore reportSemaphore = new java.util.concurrent.Semaphore(5);

    public DashboardReportServiceImpl(DashboardMetricsRepository dashboardMetricsRepository, Clock clock) {
        this.dashboardMetricsRepository = dashboardMetricsRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardReportResponse getDashboard(LocalDate requestedFrom, LocalDate requestedTo) {
        if (!reportSemaphore.tryAcquire()) {
            throw new com.dentalcare.api.exception.ServiceUnavailableException("El servidor está procesando la cantidad máxima de reportes simultáneos permitida. Por favor, intente en unos segundos.");
        }
        try {
        Instant generatedAt = clock.instant();
        LocalDate today = LocalDate.ofInstant(generatedAt, CLINIC_ZONE);
        LocalDate from = requestedFrom != null ? requestedFrom : today.withDayOfMonth(1);
        LocalDate to = requestedTo != null ? requestedTo : today;
        if (from.isAfter(to)) {
            throw new BadRequestException("Report start date must not be after end date");
        }
        if (from.plusYears(1).isBefore(to)) {
            throw new BadRequestException("Report date range cannot exceed 1 year");
        }

        Instant fromInclusive = from.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        PatientMetricsSnapshot patients = dashboardMetricsRepository.findPatientMetrics(fromInclusive, toExclusive);
        Map<AppointmentStatus, Long> appointments = dashboardMetricsRepository
                .countAppointmentsByStatus(fromInclusive, toExclusive);
        BillingMetricsSnapshot billing = dashboardMetricsRepository.findBillingMetrics(fromInclusive, toExclusive);

        long scheduled = appointments.getOrDefault(AppointmentStatus.SCHEDULED, 0L);
        long completed = appointments.getOrDefault(AppointmentStatus.COMPLETED, 0L);
        long cancelled = appointments.getOrDefault(AppointmentStatus.CANCELLED, 0L);
        BigDecimal accountBalance = billing.totalCharges().subtract(billing.totalPayments());

        return new DashboardReportResponse(
                new ReportPeriodResponse(from, to, generatedAt),
                new PatientMetricsResponse(patients.total(), patients.registeredInPeriod()),
                new AppointmentMetricsResponse(scheduled + completed + cancelled, scheduled, completed, cancelled),
                new BillingMetricsResponse(
                        money(billing.chargesCreatedInPeriod()),
                        money(billing.paymentsReceivedInPeriod()),
                        money(accountBalance.max(BigDecimal.ZERO)),
                        money(accountBalance.negate().max(BigDecimal.ZERO))));
        } finally {
            reportSemaphore.release();
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : ZERO).setScale(2, RoundingMode.UNNECESSARY);
    }
}
