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
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class DashboardReportServiceImpl implements DashboardReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final DashboardMetricsRepository dashboardMetricsRepository;
    private final Clock clock;

    public DashboardReportServiceImpl(DashboardMetricsRepository dashboardMetricsRepository, Clock clock) {
        this.dashboardMetricsRepository = dashboardMetricsRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardReportResponse getDashboard(LocalDate requestedFrom, LocalDate requestedTo) {
        Instant generatedAt = clock.instant();
        LocalDate today = LocalDate.ofInstant(generatedAt, ZoneOffset.UTC);
        LocalDate from = requestedFrom != null ? requestedFrom : today.withDayOfMonth(1);
        LocalDate to = requestedTo != null ? requestedTo : today;
        if (from.isAfter(to)) {
            throw new BadRequestException("Report start date must not be after end date");
        }

        Instant fromInclusive = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
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
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : ZERO).setScale(2, RoundingMode.UNNECESSARY);
    }
}
