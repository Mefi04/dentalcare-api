package com.dentalcare.api.modules.reports.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.reports.dto.response.DashboardReportResponse;
import com.dentalcare.api.modules.reports.repository.BillingMetricsSnapshot;
import com.dentalcare.api.modules.reports.repository.DashboardMetricsRepository;
import com.dentalcare.api.modules.reports.repository.PatientMetricsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardReportServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-01T15:30:00Z");

    @Mock
    private DashboardMetricsRepository repository;

    private DashboardReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardReportServiceImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aggregatesRealSnapshotsForExplicitInclusivePeriod() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        Instant fromInstant = Instant.parse("2026-09-01T06:00:00Z");
        Instant toExclusive = Instant.parse("2026-10-01T06:00:00Z");
        Map<AppointmentStatus, Long> appointments = new EnumMap<>(AppointmentStatus.class);
        appointments.put(AppointmentStatus.SCHEDULED, 4L);
        appointments.put(AppointmentStatus.COMPLETED, 8L);
        appointments.put(AppointmentStatus.CANCELLED, 2L);

        when(repository.findPatientMetrics(fromInstant, toExclusive))
                .thenReturn(new PatientMetricsSnapshot(50, 6));
        when(repository.countAppointmentsByStatus(fromInstant, toExclusive)).thenReturn(appointments);
        when(repository.findBillingMetrics(fromInstant, toExclusive)).thenReturn(new BillingMetricsSnapshot(
                new BigDecimal("1200.00"), new BigDecimal("950.00"),
                new BigDecimal("5000.00"), new BigDecimal("3600.00")));

        DashboardReportResponse result = service.getDashboard(from, to);

        assertThat(result.period().from()).isEqualTo(from);
        assertThat(result.period().to()).isEqualTo(to);
        assertThat(result.period().generatedAt()).isEqualTo(NOW);
        assertThat(result.patients().total()).isEqualTo(50);
        assertThat(result.patients().registeredInPeriod()).isEqualTo(6);
        assertThat(result.appointments().total()).isEqualTo(14);
        assertThat(result.appointments().scheduled()).isEqualTo(4);
        assertThat(result.appointments().completed()).isEqualTo(8);
        assertThat(result.appointments().cancelled()).isEqualTo(2);
        assertThat(result.billing().chargesCreatedInPeriod()).isEqualByComparingTo("1200.00");
        assertThat(result.billing().paymentsReceivedInPeriod()).isEqualByComparingTo("950.00");
        assertThat(result.billing().pendingBalance()).isEqualByComparingTo("1400.00");
        assertThat(result.billing().availableCredit()).isEqualByComparingTo("0.00");
    }

    @Test
    void usesGuatemalaBoundariesForExplicitSingleDayPeriod() {
        LocalDate date = LocalDate.of(2026, 10, 2);
        Instant from = Instant.parse("2026-10-02T06:00:00Z");
        Instant toExclusive = Instant.parse("2026-10-03T06:00:00Z");
        when(repository.findPatientMetrics(from, toExclusive)).thenReturn(new PatientMetricsSnapshot(0, 0));
        when(repository.countAppointmentsByStatus(from, toExclusive)).thenReturn(Map.of());
        when(repository.findBillingMetrics(from, toExclusive)).thenReturn(new BillingMetricsSnapshot(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        DashboardReportResponse result = service.getDashboard(date, date);

        assertThat(result.period().from()).isEqualTo(date);
        assertThat(result.period().to()).isEqualTo(date);
        verify(repository).findPatientMetrics(from, toExclusive);
        verify(repository).countAppointmentsByStatus(from, toExclusive);
        verify(repository).findBillingMetrics(from, toExclusive);
    }

    @Test
    void defaultsToCurrentGuatemalaMonthAndReportsCreditWithoutNegativePendingBalance() {
        Instant from = Instant.parse("2026-10-01T06:00:00Z");
        Instant toExclusive = Instant.parse("2026-10-02T06:00:00Z");
        when(repository.findPatientMetrics(from, toExclusive)).thenReturn(new PatientMetricsSnapshot(0, 0));
        when(repository.countAppointmentsByStatus(from, toExclusive)).thenReturn(Map.of());
        when(repository.findBillingMetrics(from, toExclusive)).thenReturn(new BillingMetricsSnapshot(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("125.00")));

        DashboardReportResponse result = service.getDashboard(null, null);

        assertThat(result.period().from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.period().to()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.appointments().total()).isZero();
        assertThat(result.billing().pendingBalance()).isEqualByComparingTo("0.00");
        assertThat(result.billing().availableCredit()).isEqualByComparingTo("25.00");
    }

    @Test
    void keepsPreviousGuatemalaDateWhenUtcIsAlreadyOnNextDay() {
        Instant utcNextDay = Instant.parse("2026-10-02T01:30:00Z");
        DashboardReportServiceImpl localService = new DashboardReportServiceImpl(
                repository, Clock.fixed(utcNextDay, ZoneOffset.UTC));
        Instant from = Instant.parse("2026-10-01T06:00:00Z");
        Instant toExclusive = Instant.parse("2026-10-02T06:00:00Z");
        when(repository.findPatientMetrics(from, toExclusive)).thenReturn(new PatientMetricsSnapshot(0, 0));
        when(repository.countAppointmentsByStatus(from, toExclusive)).thenReturn(Map.of());
        when(repository.findBillingMetrics(from, toExclusive)).thenReturn(new BillingMetricsSnapshot(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        DashboardReportResponse result = localService.getDashboard(null, null);

        assertThat(result.period().from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.period().to()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.period().generatedAt()).isEqualTo(utcNextDay);
        verify(repository).findPatientMetrics(from, toExclusive);
    }

    @Test
    void rejectsInvertedPeriodBeforeQuerying() {
        assertThatThrownBy(() -> service.getDashboard(
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Report start date must not be after end date");

        verify(repository, never()).findPatientMetrics(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
