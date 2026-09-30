package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AdministrativeAppointmentRepositoryIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final Instant FIRST_DATE = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant SECOND_DATE = Instant.parse("2026-10-02T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AdministrativeAppointmentRepository administrativeAppointments;
    @Autowired
    private PatientRepository patients;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;

    private Patient firstPatient;
    private Patient secondPatient;
    private User firstDentist;
    private User secondDentist;

    @BeforeEach
    void setUp() {
        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        firstPatient = patients.save(patient("3000000000001", "PAC-ADM-1"));
        secondPatient = patients.save(patient("3000000000002", "PAC-ADM-2"));
        firstDentist = users.save(user(dentistRole, "3000000000003", "Dra. Uno"));
        secondDentist = users.save(user(dentistRole, "3000000000004", "Dr. Dos"));

        administrativeAppointments.saveAllAndFlush(Set.of(
                appointment(firstPatient, firstDentist, FIRST_DATE, AppointmentStatus.SCHEDULED),
                appointment(firstPatient, secondDentist, SECOND_DATE, AppointmentStatus.COMPLETED),
                appointment(secondPatient, firstDentist, SECOND_DATE, AppointmentStatus.CANCELLED)));
    }

    @Test
    void filtersByDatePatientProfessionalAndStatus() {
        Specification<Appointment> filters = Specification.<Appointment>unrestricted()
                .and((root, query, builder) -> builder.greaterThanOrEqualTo(
                        root.get("scheduledAt"), FIRST_DATE.minusSeconds(1)))
                .and((root, query, builder) -> builder.lessThanOrEqualTo(
                        root.get("scheduledAt"), FIRST_DATE.plusSeconds(1)))
                .and((root, query, builder) -> builder.equal(
                        root.get("patient").get("id"), firstPatient.getId()))
                .and((root, query, builder) -> builder.equal(
                        root.get("professional").get("id"), firstDentist.getId()))
                .and((root, query, builder) -> builder.equal(
                        root.get("status"), AppointmentStatus.SCHEDULED));
        var result = administrativeAppointments.findAll(filters,
                PageRequest.of(0, 20, Sort.by("scheduledAt")));

        assertThat(result.getTotalElements()).isEqualTo(1);
        Appointment appointment = result.getContent().getFirst();
        assertThat(appointment.getPatient().getId()).isEqualTo(firstPatient.getId());
        assertThat(appointment.getProfessional().getId()).isEqualTo(firstDentist.getId());
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void supportsUnfilteredAgendaAndLoadsDetailRelationships() {
        var page = administrativeAppointments.findAll(
                Specification.unrestricted(),
                PageRequest.of(0, 20, Sort.by("scheduledAt")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        Appointment detail = administrativeAppointments
                .findDetailedById(page.getContent().getFirst().getId()).orElseThrow();
        assertThat(detail.getPatient().getName()).isNotBlank();
        assertThat(detail.getProfessional().getFullName()).isNotBlank();

        Appointment lockedDetail = administrativeAppointments
                .findDetailedByIdForUpdate(detail.getId()).orElseThrow();
        assertThat(lockedDetail.getId()).isEqualTo(detail.getId());
    }

    private Patient patient(String dpi, String code) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName("Paciente " + code);
        patient.setDpi(dpi);
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setGender(Gender.OTHER);
        patient.setPhone("5555-0101");
        patient.setCreatedAt(NOW);
        patient.setUpdatedAt(NOW);
        return patient;
    }

    private User user(Role role, String cui, String fullName) {
        User user = new User(UUID.randomUUID(), "dentist-" + cui, fullName,
                "dentist-" + cui + "@example.test", cui, "hash",
                UserStatus.ACTIVE, NOW, NOW);
        user.setRoles(Set.of(role));
        return user;
    }

    private Appointment appointment(Patient patient, User dentist, Instant date, AppointmentStatus status) {
        return new Appointment(UUID.randomUUID(), patient, dentist, date, status, NOW, NOW);
    }
}
