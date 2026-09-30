package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final Instant SCHEDULED_AT = Instant.parse("2026-09-28T15:00:00Z");

    @Mock AppointmentRepository appointments;
    @Mock PatientRepository patients;
    @Mock UserRepository users;

    private AppointmentServiceImpl service;
    private Patient patient;
    private User dentist;

    @BeforeEach
    void setUp() {
        service = new AppointmentServiceImpl(appointments, patients, users,
                Clock.fixed(NOW, ZoneOffset.UTC));
        patient = new Patient();
        patient.setId(UUID.randomUUID());
        dentist = userWithRole("DENTIST", true, UserStatus.ACTIVE);
    }

    @Test
    void createsScheduledAppointmentWithPatientProfessionalAndAuditTimestamps() {
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(appointments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = service.create(patient.getId(), dentist.getId(), SCHEDULED_AT);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getPatient()).isSameAs(patient);
        assertThat(result.getProfessional()).isSameAs(dentist);
        assertThat(result.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsMissingPatient() {
        when(patients.findById(patient.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(patient.getId(), dentist.getId(), SCHEDULED_AT))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Patient not found");
        verify(users, never()).findWithRolesById(any());
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingProfessional() {
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(patient.getId(), dentist.getId(), SCHEDULED_AT))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Professional not found");
    }

    @Test
    void rejectsProfessionalWithoutDentistRole() {
        User secretary = userWithRole("SECRETARY", true, UserStatus.ACTIVE);
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(users.findWithRolesById(secretary.getId())).thenReturn(Optional.of(secretary));

        assertThatThrownBy(() -> service.create(patient.getId(), secretary.getId(), SCHEDULED_AT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Professional is not an active dentist");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInactiveProfessionalOrInactiveDentistRole() {
        User inactiveUser = userWithRole("DENTIST", true, UserStatus.INACTIVE);
        User inactiveRole = userWithRole("DENTIST", false, UserStatus.ACTIVE);
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(users.findWithRolesById(inactiveUser.getId())).thenReturn(Optional.of(inactiveUser));
        when(users.findWithRolesById(inactiveRole.getId())).thenReturn(Optional.of(inactiveRole));

        assertThatThrownBy(() -> service.create(patient.getId(), inactiveUser.getId(), SCHEDULED_AT))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.create(patient.getId(), inactiveRole.getId(), SCHEDULED_AT))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsMissingRequiredIdentifiersAndDate() {
        assertThatThrownBy(() -> service.create(null, dentist.getId(), SCHEDULED_AT))
                .isInstanceOf(BadRequestException.class).hasMessage("Patient id is required");
        assertThatThrownBy(() -> service.create(patient.getId(), null, SCHEDULED_AT))
                .isInstanceOf(BadRequestException.class).hasMessage("Professional id is required");
        assertThatThrownBy(() -> service.create(patient.getId(), dentist.getId(), null))
                .isInstanceOf(BadRequestException.class).hasMessage("Appointment date and time are required");
    }

    @Test
    void rejectsPastOrCurrentAppointmentTime() {
        assertThatThrownBy(() -> service.create(patient.getId(), dentist.getId(), NOW.minusSeconds(1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointment date and time must be in the future");
        assertThatThrownBy(() -> service.create(patient.getId(), dentist.getId(), NOW))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointment date and time must be in the future");
        verify(patients, never()).findById(any());
    }

    @Test
    void rejectsExistingScheduledCollision() {
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(appointments.existsByProfessional_IdAndScheduledAtAndStatus(
                dentist.getId(), SCHEDULED_AT, AppointmentStatus.SCHEDULED)).thenReturn(true);

        assertThatThrownBy(() -> service.create(patient.getId(), dentist.getId(), SCHEDULED_AT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Appointment time is not available");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void translatesDatabaseCollisionWithoutLeakingSqlDetails() {
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(appointments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("constraint details"));

        assertThatThrownBy(() -> service.create(patient.getId(), dentist.getId(), SCHEDULED_AT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Appointment time is not available");
    }

    @Test
    void findsAppointmentByIdAndRejectsUnknownId() {
        UUID id = UUID.randomUUID();
        Appointment appointment = new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, NOW, NOW);
        when(appointments.findById(id)).thenReturn(Optional.of(appointment));

        assertThat(service.findById(id)).isSameAs(appointment);

        UUID unknown = UUID.randomUUID();
        when(appointments.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(unknown))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Appointment not found");
    }

    @Test
    void cancelsScheduledAppointmentUpdatingStatusAndTimestamp() {
        UUID id = UUID.randomUUID();
        Instant createdAt = NOW.minusSeconds(3600);
        Appointment appointment = new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, createdAt, createdAt);
        when(appointments.saveAndFlush(appointment)).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = service.cancel(appointment);

        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        verify(appointments).saveAndFlush(appointment);
    }

    @Test
    void rejectsCancellingNullAppointment() {
        assertThatThrownBy(() -> service.cancel(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointment is required");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCancellingAlreadyCancelledAppointment() {
        UUID id = UUID.randomUUID();
        Appointment appointment = new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.CANCELLED, NOW.minusSeconds(3600), NOW.minusSeconds(3600));

        assertThatThrownBy(() -> service.cancel(appointment))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only scheduled appointments can be cancelled");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCancellingCompletedAppointment() {
        UUID id = UUID.randomUUID();
        Appointment appointment = new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.COMPLETED, NOW.minusSeconds(3600), NOW.minusSeconds(3600));

        assertThatThrownBy(() -> service.cancel(appointment))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only scheduled appointments can be cancelled");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void reschedulesScheduledAppointmentAndUpdatesTimestamp() {
        Appointment appointment = new Appointment(UUID.randomUUID(), patient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, NOW.minusSeconds(3600), NOW.minusSeconds(3600));
        Instant newDate = SCHEDULED_AT.plusSeconds(7200);
        when(appointments.saveAndFlush(appointment)).thenReturn(appointment);

        Appointment result = service.reschedule(appointment, newDate);

        assertThat(result.getScheduledAt()).isEqualTo(newDate);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        verify(appointments).existsByProfessional_IdAndScheduledAtAndStatusAndIdNot(
                dentist.getId(), newDate, AppointmentStatus.SCHEDULED, appointment.getId());
        verify(appointments).saveAndFlush(appointment);
    }

    @Test
    void rescheduleRejectsInvalidDateAndNonScheduledStates() {
        Appointment completed = new Appointment(UUID.randomUUID(), patient, dentist, SCHEDULED_AT,
                AppointmentStatus.COMPLETED, NOW, NOW);
        Appointment cancelled = new Appointment(UUID.randomUUID(), patient, dentist, SCHEDULED_AT,
                AppointmentStatus.CANCELLED, NOW, NOW);

        assertThatThrownBy(() -> service.reschedule(completed, SCHEDULED_AT.plusSeconds(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only scheduled appointments can be rescheduled");
        assertThatThrownBy(() -> service.reschedule(cancelled, SCHEDULED_AT.plusSeconds(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only scheduled appointments can be rescheduled");
        assertThatThrownBy(() -> service.reschedule(completed, NOW))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointment date and time must be in the future");
        assertThatThrownBy(() -> service.reschedule(completed, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointment date and time are required");
    }

    @Test
    void rescheduleRejectsDetectedAndDatabaseScheduleConflicts() {
        Appointment appointment = new Appointment(UUID.randomUUID(), patient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, NOW, NOW);
        Instant newDate = SCHEDULED_AT.plusSeconds(7200);
        when(appointments.existsByProfessional_IdAndScheduledAtAndStatusAndIdNot(
                dentist.getId(), newDate, AppointmentStatus.SCHEDULED, appointment.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.reschedule(appointment, newDate))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Appointment time is not available");

        Instant anotherDate = newDate.plusSeconds(3600);
        when(appointments.saveAndFlush(appointment))
                .thenThrow(new DataIntegrityViolationException("constraint details"));
        assertThatThrownBy(() -> service.reschedule(appointment, anotherDate))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Appointment time is not available");
    }

    private User userWithRole(String roleCode, boolean roleActive, UserStatus status) {
        User user = new User(UUID.randomUUID(), "staff-" + UUID.randomUUID(), "Professional",
                "professional@example.test", "1234567890123", "hash", status, NOW, NOW);
        user.setRoles(Set.of(new Role(UUID.randomUUID(), roleCode, roleCode, null, roleActive)));
        return user;
    }
}
