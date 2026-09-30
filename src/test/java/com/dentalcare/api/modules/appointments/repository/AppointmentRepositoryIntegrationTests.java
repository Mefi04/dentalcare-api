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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.Optional;
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
    void cancellingAppointmentUpdatesStatusAndTimestampWithoutDeletingRow() {
        UUID id = UUID.randomUUID();
        Appointment appointment = appointments.saveAndFlush(new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, NOW, NOW));
        long countBefore = appointments.count();

        Instant cancelledAt = NOW.plusSeconds(300);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setUpdatedAt(cancelledAt);
        appointments.saveAndFlush(appointment);

        assertThat(appointments.count()).isEqualTo(countBefore);

        Appointment recovered = appointments.findById(id).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(recovered.getCreatedAt()).isEqualTo(NOW);
        assertThat(recovered.getUpdatedAt()).isEqualTo(cancelledAt);

        Optional<Appointment> owned = appointments.findByIdAndPatient_Id(id, patient.getId());
        assertThat(owned).isPresent();
        assertThat(owned.get().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        // Slot is freed for another scheduled appointment with the same dentist
        Patient otherPatient = patients.save(patient("2000000000005"));
        Appointment newAppointment = appointments.saveAndFlush(new Appointment(
                UUID.randomUUID(), otherPatient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, cancelledAt, cancelledAt));
        assertThat(newAppointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
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

    @Test
    void listsOnlyRequestedPatientsAppointmentsInDeterministicOrder() {
        Patient otherPatient = patients.save(patient("2000000000003"));
        Appointment older = appointment(patient, Instant.parse("2026-10-01T10:00:00Z"));
        Appointment newer = appointment(patient, Instant.parse("2026-10-02T10:00:00Z"));
        appointments.saveAllAndFlush(Set.of(older, newer, appointment(otherPatient,
                Instant.parse("2026-10-03T10:00:00Z"))));

        var result = appointments.findByPatient_Id(patient.getId(), PageRequest.of(0, 10,
                Sort.by(Sort.Order.desc("scheduledAt"), Sort.Order.desc("id"))));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Appointment::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(result.getContent()).allMatch(value -> value.getPatient().getId().equals(patient.getId()));
    }

    @Test
    void detailQueryDoesNotReturnAnotherPatientsAppointment() {
        Patient otherPatient = patients.save(patient("2000000000003"));
        Appointment foreignAppointment = appointments.saveAndFlush(appointment(otherPatient, SCHEDULED_AT));

        Optional<Appointment> result = appointments.findByIdAndPatient_Id(
                foreignAppointment.getId(), patient.getId());

        assertThat(result).isEmpty();
        assertThat(appointments.findByIdAndPatient_Id(foreignAppointment.getId(), otherPatient.getId()))
                .isPresent();
    }

    @Test
    void postgresRejectsTwoScheduledAppointmentsForSameProfessionalAndInstant() {
        appointments.saveAndFlush(appointment(patient, SCHEDULED_AT));

        assertThatThrownBy(() -> appointments.saveAndFlush(appointment(patient, SCHEDULED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancelledAppointmentDoesNotBlockScheduledAppointmentAtSameInstant() {
        Appointment cancelled = new Appointment(UUID.randomUUID(), patient, dentist, SCHEDULED_AT,
                AppointmentStatus.CANCELLED, NOW, NOW);
        Appointment scheduled = appointment(patient, SCHEDULED_AT);

        appointments.saveAllAndFlush(Set.of(cancelled, scheduled));

        assertThat(appointments.findById(cancelled.getId())).isPresent();
        assertThat(appointments.findById(scheduled.getId())).isPresent();
    }

    @Test
    void differentProfessionalsCanBeScheduledAtSameInstant() {
        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        User otherDentist = users.save(user(dentistRole, "2000000000004"));

        appointments.saveAllAndFlush(Set.of(
                appointment(patient, SCHEDULED_AT),
                new Appointment(UUID.randomUUID(), patient, otherDentist, SCHEDULED_AT,
                        AppointmentStatus.SCHEDULED, NOW, NOW)));

        assertThat(appointments.count()).isEqualTo(2);
    }

    @Test
    void activeDentistCatalogExcludesInactiveUsersAndInactiveRoles() {
        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        assertThat(users.findActiveDentists()).extracting(User::getId)
                .containsExactly(dentist.getId());

        User inactiveUser = user(dentistRole, "2000000000004");
        inactiveUser.setStatus(UserStatus.INACTIVE);
        users.saveAndFlush(inactiveUser);

        assertThat(users.findActiveDentists()).extracting(User::getId)
                .containsExactly(dentist.getId());

        dentistRole.setActive(false);
        roles.saveAndFlush(dentistRole);
        assertThat(users.findActiveDentists()).isEmpty();
    }

    private Patient patient() {
        return patient("2000000000001");
    }

    private Patient patient(String dpi) {
        Patient result = new Patient();
        result.setId(UUID.randomUUID());
        result.setCode("PAT-" + UUID.randomUUID().toString().substring(0, 8));
        result.setName("Appointment Patient");
        result.setDpi(dpi);
        result.setBirthDate(LocalDate.of(1990, 1, 1));
        result.setGender(Gender.OTHER);
        result.setPhone("55550000");
        result.setCreatedAt(NOW);
        result.setUpdatedAt(NOW);
        return result;
    }

    private Appointment appointment(Patient owner, Instant scheduledAt) {
        return new Appointment(UUID.randomUUID(), owner, dentist, scheduledAt,
                AppointmentStatus.SCHEDULED, NOW, NOW);
    }

    private User user(Role role) {
        return user(role, "2000000000002");
    }

    private User user(Role role, String cui) {
        User result = new User(UUID.randomUUID(), "dentist-" + UUID.randomUUID(), "Dentist",
                "dentist-" + UUID.randomUUID() + "@example.test", cui, "hash",
                UserStatus.ACTIVE, NOW, NOW);
        result.setRoles(Set.of(role));
        return result;
    }
}
