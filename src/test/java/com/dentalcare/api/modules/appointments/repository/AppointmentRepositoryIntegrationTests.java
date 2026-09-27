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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AppointmentRepositoryIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final Instant SCHEDULED_AT = Instant.parse("2026-09-28T15:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AppointmentRepository appointments;
    @Autowired PatientRepository patients;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;

    private Patient patient;
    private User dentist;

    @BeforeEach
    void setUp() {
        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        dentist = users.save(user(dentistRole));
        patient = patients.save(patient());
    }

    @Test
    void persistsAndRecoversAppointmentWithItsRelationshipsAndInitialStatus() {
        UUID id = UUID.randomUUID();
        appointments.saveAndFlush(new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, NOW, NOW));

        Appointment recovered = appointments.findById(id).orElseThrow();

        assertThat(recovered.getPatient().getId()).isEqualTo(patient.getId());
        assertThat(recovered.getProfessional().getId()).isEqualTo(dentist.getId());
        assertThat(recovered.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(recovered.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(recovered.getCreatedAt()).isEqualTo(NOW);
        assertThat(recovered.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void persistsExplicitSupportedStatus() {
        UUID id = UUID.randomUUID();
        appointments.saveAndFlush(new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.COMPLETED, NOW, NOW));

        assertThat(appointments.findById(id).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void rejectsUnknownPatientForeignKey() {
        UUID missingPatientId = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO appointments
                            (id, patient_id, professional_id, scheduled_at, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), missingPatientId, dentist.getId(), Timestamp.from(SCHEDULED_AT),
                AppointmentStatus.SCHEDULED.name(), Timestamp.from(NOW), Timestamp.from(NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Patient patient() {
        Patient result = new Patient();
        result.setId(UUID.randomUUID());
        result.setCode("PAT-" + UUID.randomUUID().toString().substring(0, 8));
        result.setName("Appointment Patient");
        result.setDpi("2000000000001");
        result.setBirthDate(LocalDate.of(1990, 1, 1));
        result.setGender(Gender.OTHER);
        result.setPhone("55550000");
        result.setCreatedAt(NOW);
        result.setUpdatedAt(NOW);
        return result;
    }

    private User user(Role role) {
        User result = new User(UUID.randomUUID(), "dentist-" + UUID.randomUUID(), "Dentist",
                "dentist-" + UUID.randomUUID() + "@example.test", "2000000000002", "hash",
                UserStatus.ACTIVE, NOW, NOW);
        result.setRoles(Set.of(role));
        return result;
    }
}
