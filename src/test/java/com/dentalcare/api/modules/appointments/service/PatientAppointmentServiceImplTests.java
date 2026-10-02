package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.dto.response.PatientAppointmentResponse;
import com.dentalcare.api.modules.appointments.mapper.AppointmentMapper;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientAppointmentServiceImplTests {
    @Mock AppointmentRepository appointments;
    @Mock PatientRepository patients;
    @Mock AppointmentService appointmentService;
    @Mock UserRepository users;
    @Mock WaitingRoomService waitingRoomService;

    private PatientAppointmentServiceImpl service;
    private UUID userId;
    private Patient patient;

    @BeforeEach
    void setUp() {
        service = new PatientAppointmentServiceImpl(
                appointments, patients, new AppointmentMapper(), appointmentService, users, waitingRoomService);
        userId = UUID.randomUUID();
        patient = new Patient();
        patient.setId(UUID.randomUUID());
    }

    @Test
    void reschedulesOnlyOwnedAppointmentUsingLockedOwnershipQuery() {
        Appointment appointment = appointment(patient.getId());
        Instant newDate = appointment.getScheduledAt().plusSeconds(7200);
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_IdForUpdate(appointment.getId(), patient.getId()))
                .thenReturn(Optional.of(appointment));
        when(appointmentService.reschedule(appointment, newDate)).thenAnswer(invocation -> {
            appointment.setScheduledAt(newDate);
            return appointment;
        });

        PatientAppointmentResponse result = service.rescheduleCurrentPatientAppointment(
                userId, appointment.getId(), newDate);

        assertThat(result.scheduledAt()).isEqualTo(newDate);
        verify(appointments).findByIdAndPatient_IdForUpdate(appointment.getId(), patient.getId());
        verify(appointmentService).reschedule(appointment, newDate);
    }

    @Test
    void foreignAndUnknownRescheduleUseSameNotFoundResult() {
        UUID foreignOrUnknownId = UUID.randomUUID();
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_IdForUpdate(foreignOrUnknownId, patient.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rescheduleCurrentPatientAppointment(
                userId, foreignOrUnknownId, Instant.parse("2026-10-15T12:00:00Z")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment not found");
        verify(appointmentService, never()).reschedule(any(), any());
        verify(appointments, never()).findById(any());
    }

    @Test
    void listsOnlyResolvedPatientsAppointmentsWithDeterministicPagination() {
        Appointment appointment = appointment(patient.getId());
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByPatient_Id(eq(patient.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(appointment)));

        var result = service.findCurrentPatientAppointments(userId, 1, 250);

        assertThat(result.getContent()).extracting(PatientAppointmentResponse::id)
                .containsExactly(appointment.getId());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(appointments).findByPatient_Id(eq(patient.getId()), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("scheduledAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void returnsEmptyPageAndRejectsInvalidPagination() {
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByPatient_Id(eq(patient.getId()), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.findCurrentPatientAppointments(userId, 0, 20)).isEmpty();
        assertThatThrownBy(() -> service.findCurrentPatientAppointments(userId, -1, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.findCurrentPatientAppointments(userId, 0, 0))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void detailUsesCombinedAppointmentAndPatientQuery() {
        Appointment appointment = appointment(patient.getId());
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(appointment.getId(), patient.getId()))
                .thenReturn(Optional.of(appointment));

        PatientAppointmentResponse result = service.findCurrentPatientAppointment(userId, appointment.getId());

        assertThat(result.id()).isEqualTo(appointment.getId());
        verify(appointments, never()).findById(any());
    }

    @Test
    void missingOrForeignAppointmentHasSameNotFoundResponse() {
        UUID appointmentId = UUID.randomUUID();
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(appointmentId, patient.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findCurrentPatientAppointment(userId, appointmentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment not found");
        verify(appointments, never()).findById(any());
    }

    @Test
    void rejectsAuthenticatedUserWithoutLinkedPatient() {
        when(patients.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findCurrentPatientAppointments(userId, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
        verify(appointments, never()).findByPatient_Id(any(), any());
    }

    @Test
    void createsAppointmentForPatientResolvedFromAuthenticatedUser() {
        Appointment appointment = appointment(patient.getId());
        UUID professionalId = appointment.getProfessional().getId();
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointmentService.create(patient.getId(), professionalId, appointment.getScheduledAt()))
                .thenReturn(appointment);

        PatientAppointmentResponse result = service.createCurrentPatientAppointment(
                userId, professionalId, appointment.getScheduledAt());

        assertThat(result.id()).isEqualTo(appointment.getId());
        assertThat(result.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(appointmentService).create(patient.getId(), professionalId, appointment.getScheduledAt());
    }

    @Test
    void catalogMapsOnlySafeProfessionalFieldsInRepositoryOrder() {
        User first = professional("Dra. Ana López");
        User second = professional("Dr. Carlos Ruiz");
        when(users.findActiveDentists()).thenReturn(List.of(first, second));

        var result = service.findAvailableProfessionals();

        assertThat(result).extracting(value -> value.fullName())
                .containsExactly("Dra. Ana López", "Dr. Carlos Ruiz");
        assertThat(result).extracting(value -> value.id())
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void cancelsOnlyOwnedScheduledAppointmentDelegatingToAppointmentService() {
        Appointment appointment = appointment(patient.getId());
        Appointment cancelled = new Appointment(
                appointment.getId(), appointment.getPatient(), appointment.getProfessional(),
                appointment.getScheduledAt(), AppointmentStatus.CANCELLED,
                appointment.getCreatedAt(), Instant.now());
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(appointment.getId(), patient.getId()))
                .thenReturn(Optional.of(appointment));
        when(appointmentService.cancel(appointment)).thenReturn(cancelled);

        PatientAppointmentResponse result = service.cancelCurrentPatientAppointment(userId, appointment.getId());

        assertThat(result.id()).isEqualTo(appointment.getId());
        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(appointmentService).cancel(appointment);
        verify(waitingRoomService).closeForAppointment(userId, appointment.getId());
        verify(appointments, never()).findById(any());
    }

    @Test
    void cancelRejectsNullAppointmentId() {
        assertThatThrownBy(() -> service.cancelCurrentPatientAppointment(userId, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointment id is required");
        verify(appointments, never()).findByIdAndPatient_Id(any(), any());
    }

    @Test
    void cancelRejectsAuthenticatedUserWithoutLinkedPatient() {
        UUID appointmentId = UUID.randomUUID();
        when(patients.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelCurrentPatientAppointment(userId, appointmentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Patient not found");
        verify(appointments, never()).findByIdAndPatient_Id(any(), any());
    }

    @Test
    void cancelRejectsMissingOrForeignAppointmentWithSameNotFoundResponse() {
        UUID foreignOrMissingId = UUID.randomUUID();
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(foreignOrMissingId, patient.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelCurrentPatientAppointment(userId, foreignOrMissingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment not found");
        verify(appointmentService, never()).cancel(any());
        verify(appointments, never()).findById(any());
    }

    @Test
    void cancelPropagatesConflictExceptionFromAppointmentService() {
        Appointment appointment = appointment(patient.getId());
        when(patients.findByUser_Id(userId)).thenReturn(Optional.of(patient));
        when(appointments.findByIdAndPatient_Id(appointment.getId(), patient.getId()))
                .thenReturn(Optional.of(appointment));
        when(appointmentService.cancel(appointment))
                .thenThrow(new ConflictException("Only scheduled appointments can be cancelled"));

        assertThatThrownBy(() -> service.cancelCurrentPatientAppointment(userId, appointment.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only scheduled appointments can be cancelled");
    }

    private Appointment appointment(UUID patientId) {
        Patient owner = new Patient();
        owner.setId(patientId);
        User professional = new User();
        professional.setId(UUID.randomUUID());
        professional.setFullName("Dra. Ana López");
        Instant scheduledAt = Instant.parse("2026-10-10T15:00:00Z");
        return new Appointment(UUID.randomUUID(), owner, professional, scheduledAt,
                AppointmentStatus.SCHEDULED, scheduledAt.minusSeconds(3600), scheduledAt.minusSeconds(3600));
    }

    private User professional(String fullName) {
        User professional = new User();
        professional.setId(UUID.randomUUID());
        professional.setFullName(fullName);
        professional.setEmail("private@example.test");
        professional.setPasswordHash("secret-hash");
        return professional;
    }
}
