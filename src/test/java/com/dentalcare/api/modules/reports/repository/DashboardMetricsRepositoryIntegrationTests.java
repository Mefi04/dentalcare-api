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
        Instant instant = Instant.parse(scheduledAt);
        return new Appointment(UUID.randomUUID(), patient, professional, instant, status, instant, instant);
    }

    private Charge charge(Patient patient, String concept, String amount, String createdAt) {
        return new Charge(UUID.randomUUID(), patient, concept, new BigDecimal(amount), Instant.parse(createdAt));
    }

    private Payment payment(Patient patient, Charge charge, String amount, String createdAt) {
        return new Payment(UUID.randomUUID(), patient, charge, PaymentKind.PARTIAL_PAYMENT, PaymentMethod.CASH,
                new BigDecimal(amount), Instant.parse(createdAt));
    }
}
