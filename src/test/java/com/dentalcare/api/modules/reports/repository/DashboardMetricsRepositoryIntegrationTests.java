package com.dentalcare.api.modules.reports.repository;

import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.billing.model.Charge;
import com.dentalcare.api.modules.billing.model.Payment;
import com.dentalcare.api.modules.billing.model.PaymentKind;
import com.dentalcare.api.modules.billing.model.PaymentMethod;
import com.dentalcare.api.modules.billing.repository.ChargeRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(JpaDashboardMetricsRepository.class)
class DashboardMetricsRepositoryIntegrationTests {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO_EXCLUSIVE = Instant.parse("2026-10-01T00:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DashboardMetricsRepository metrics;
    @Autowired PatientRepository patients;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AppointmentRepository appointments;
    @Autowired ChargeRepository charges;
    @Autowired PaymentRepository payments;

    @Test
    void aggregatesPersistedRowsByInclusiveExclusivePeriodWithoutLoadingEntities() {
        Patient recent = patients.save(patient("PAC-REPORT-1", "7000000000001",
                Instant.parse("2026-09-10T12:00:00Z")));
        Patient older = patients.save(patient("PAC-REPORT-2", "7000000000002",
                Instant.parse("2026-08-10T12:00:00Z")));
        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        User dentist = users.save(user(dentistRole));

        appointments.saveAllAndFlush(List.of(
                appointment(recent, dentist, "2026-09-05T10:00:00Z", AppointmentStatus.SCHEDULED),
                appointment(recent, dentist, "2026-09-06T10:00:00Z", AppointmentStatus.COMPLETED),
                appointment(older, dentist, "2026-09-07T10:00:00Z", AppointmentStatus.CANCELLED),
                appointment(older, dentist, "2026-10-02T10:00:00Z", AppointmentStatus.SCHEDULED)));

        Charge periodCharge = charges.saveAndFlush(charge(recent, "Tratamiento", "300.00",
                "2026-09-08T10:00:00Z"));
        Charge oldCharge = charges.saveAndFlush(charge(older, "Consulta", "200.00",
                "2026-08-08T10:00:00Z"));
        payments.saveAllAndFlush(List.of(
                payment(recent, periodCharge, "100.00", "2026-09-09T10:00:00Z"),
                payment(older, oldCharge, "50.00", "2026-08-09T10:00:00Z")));

        PatientMetricsSnapshot patientMetrics = metrics.findPatientMetrics(FROM, TO_EXCLUSIVE);
        Map<AppointmentStatus, Long> appointmentMetrics = metrics.countAppointmentsByStatus(FROM, TO_EXCLUSIVE);
        BillingMetricsSnapshot billingMetrics = metrics.findBillingMetrics(FROM, TO_EXCLUSIVE);

        assertThat(patientMetrics.total()).isEqualTo(2);
        assertThat(patientMetrics.registeredInPeriod()).isEqualTo(1);
        assertThat(appointmentMetrics).containsEntry(AppointmentStatus.SCHEDULED, 1L)
                .containsEntry(AppointmentStatus.COMPLETED, 1L)
                .containsEntry(AppointmentStatus.CANCELLED, 1L);
        assertThat(billingMetrics.chargesCreatedInPeriod()).isEqualByComparingTo("300.00");
        assertThat(billingMetrics.paymentsReceivedInPeriod()).isEqualByComparingTo("100.00");
        assertThat(billingMetrics.totalCharges()).isEqualByComparingTo("500.00");
        assertThat(billingMetrics.totalPayments()).isEqualByComparingTo("150.00");
    }

    @Test
    void appliesGuatemalaDayBoundariesAsInclusiveExclusiveInstants() {
        Instant fromInclusive = Instant.parse("2026-10-02T06:00:00Z");
        Instant toExclusive = Instant.parse("2026-10-03T06:00:00Z");
        Instant justBeforeStart = Instant.parse("2026-10-02T05:59:59.999999Z");
        Instant inside = Instant.parse("2026-10-02T18:00:00Z");
        Instant justBeforeEnd = Instant.parse("2026-10-03T05:59:59.999999Z");

        Patient before = patients.save(patient("PAC-BOUNDARY-1", "7100000000001", justBeforeStart));
        Patient atStart = patients.save(patient("PAC-BOUNDARY-2", "7100000000002", fromInclusive));
        Patient inPeriod = patients.save(patient("PAC-BOUNDARY-3", "7100000000003", inside));
        Patient beforeEnd = patients.save(patient("PAC-BOUNDARY-4", "7100000000004", justBeforeEnd));
        Patient atEnd = patients.save(patient("PAC-BOUNDARY-5", "7100000000005", toExclusive));
        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        User dentist = users.save(user(dentistRole));

        appointments.saveAllAndFlush(List.of(
                appointment(before, dentist, justBeforeStart, AppointmentStatus.SCHEDULED),
                appointment(atStart, dentist, fromInclusive, AppointmentStatus.COMPLETED),
                appointment(beforeEnd, dentist, justBeforeEnd, AppointmentStatus.CANCELLED),
                appointment(atEnd, dentist, toExclusive, AppointmentStatus.SCHEDULED)));

        Charge chargeBefore = charges.saveAndFlush(charge(before, "Before boundary", "1.00", justBeforeStart));
        Charge chargeAtStart = charges.saveAndFlush(charge(atStart, "At start", "10.00", fromInclusive));
        Charge chargeBeforeEnd = charges.saveAndFlush(charge(beforeEnd, "Before end", "100.00", justBeforeEnd));
        Charge chargeAtEnd = charges.saveAndFlush(charge(atEnd, "At end", "1000.00", toExclusive));
        payments.saveAllAndFlush(List.of(
                payment(before, chargeBefore, "0.10", justBeforeStart),
                payment(atStart, chargeAtStart, "1.00", fromInclusive),
                payment(beforeEnd, chargeBeforeEnd, "10.00", justBeforeEnd),
                payment(atEnd, chargeAtEnd, "100.00", toExclusive)));

        PatientMetricsSnapshot patientMetrics = metrics.findPatientMetrics(fromInclusive, toExclusive);
        PatientMetricsSnapshot patientsAtStart = metrics.findPatientMetrics(fromInclusive, inside);
        PatientMetricsSnapshot patientsBeforeEnd = metrics.findPatientMetrics(justBeforeEnd, toExclusive);
        PatientMetricsSnapshot patientsBeforeStart = metrics.findPatientMetrics(justBeforeStart, fromInclusive);
        PatientMetricsSnapshot patientsAtEnd = metrics.findPatientMetrics(toExclusive, toExclusive.plusNanos(1_000));
        Map<AppointmentStatus, Long> appointmentMetrics =
                metrics.countAppointmentsByStatus(fromInclusive, toExclusive);
        BillingMetricsSnapshot billingMetrics = metrics.findBillingMetrics(fromInclusive, toExclusive);

        assertThat(patientMetrics.total()).isEqualTo(5);
        assertThat(patientMetrics.registeredInPeriod()).isEqualTo(3);
        assertThat(patientsAtStart.registeredInPeriod()).isEqualTo(1);
        assertThat(patientsBeforeEnd.registeredInPeriod()).isEqualTo(1);
        assertThat(patientsBeforeStart.registeredInPeriod()).isEqualTo(1);
        assertThat(patientsAtEnd.registeredInPeriod()).isEqualTo(1);
        assertThat(appointmentMetrics)
                .containsOnlyKeys(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED)
                .containsEntry(AppointmentStatus.COMPLETED, 1L)
                .containsEntry(AppointmentStatus.CANCELLED, 1L);
        assertThat(billingMetrics.chargesCreatedInPeriod()).isEqualByComparingTo("110.00");
        assertThat(billingMetrics.paymentsReceivedInPeriod()).isEqualByComparingTo("11.00");
        assertThat(billingMetrics.totalCharges()).isEqualByComparingTo("1111.00");
        assertThat(billingMetrics.totalPayments()).isEqualByComparingTo("111.10");
    }

    private Patient patient(String code, String dpi, Instant createdAt) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName("Report Patient");
        patient.setDpi(dpi);
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setGender(Gender.OTHER);
        patient.setPhone("55550000");
        patient.setCreatedAt(createdAt);
        patient.setUpdatedAt(createdAt);
        return patient;
    }

    private User user(Role role) {
        User user = new User(UUID.randomUUID(), "report-dentist", "Report Dentist",
                "report-dentist@example.test", "7000000000003", "hash", UserStatus.ACTIVE, FROM, FROM);
        user.setRoles(Set.of(role));
        return user;
    }

    private Appointment appointment(Patient patient, User professional, String scheduledAt,
                                    AppointmentStatus status) {
        return appointment(patient, professional, Instant.parse(scheduledAt), status);
    }

    private Appointment appointment(Patient patient, User professional, Instant instant,
                                    AppointmentStatus status) {
        return new Appointment(UUID.randomUUID(), patient, professional, instant, status, instant, instant);
    }

    private Charge charge(Patient patient, String concept, String amount, String createdAt) {
        return charge(patient, concept, amount, Instant.parse(createdAt));
    }

    private Charge charge(Patient patient, String concept, String amount, Instant createdAt) {
        return new Charge(UUID.randomUUID(), patient, concept, new BigDecimal(amount), createdAt);
    }

    private Payment payment(Patient patient, Charge charge, String amount, String createdAt) {
        return payment(patient, charge, amount, Instant.parse(createdAt));
    }

    private Payment payment(Patient patient, Charge charge, String amount, Instant createdAt) {
        return new Payment(UUID.randomUUID(), patient, charge, PaymentKind.PARTIAL_PAYMENT, PaymentMethod.CASH,
                new BigDecimal(amount), createdAt);
    }
}
