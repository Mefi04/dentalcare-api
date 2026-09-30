package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.dto.request.CreateAdministrativeAppointmentRequest;
import com.dentalcare.api.modules.appointments.mapper.AdministrativeAppointmentMapper;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.repository.AdministrativeAppointmentRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class AdministrativeAppointmentServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final Instant FUTURE = Instant.parse("2026-10-01T15:00:00Z");

    @Mock
    private AdministrativeAppointmentRepository administrativeAppointmentRepository;
    @Mock
    private AppointmentService appointmentService;

    private AdministrativeAppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdministrativeAppointmentServiceImpl(
                administrativeAppointmentRepository,
                appointmentService,
                new AdministrativeAppointmentMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listsAppointmentsUsingEveryAdministrativeFilter() {
        Instant from = FUTURE.minusSeconds(3600);
        Instant to = FUTURE.plusSeconds(3600);
        UUID patientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        Appointment appointment = appointment(patientId, professionalId, FUTURE, AppointmentStatus.SCHEDULED);
        when(administrativeAppointmentRepository.findAll(
                ArgumentMatchers.<Specification<Appointment>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(appointment)));

        var result = service.findAll(from, to, patientId, professionalId,
                AppointmentStatus.SCHEDULED, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().patient().id()).isEqualTo(patientId);
        assertThat(result.getContent().getFirst().professional().id()).isEqualTo(professionalId);
    }

    @Test
    void rejectsInvalidRangeAndPagination() {
        assertThatThrownBy(() -> service.findAll(FUTURE, FUTURE.minusSeconds(1),
                null, null, null, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("From date must not be after to date");
        assertThatThrownBy(() -> service.findAll(null, null,
                null, null, null, -1, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.findAll(null, null,
                null, null, null, 0, 0))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void returnsDetailedAppointmentAndRejectsMissingOne() {
        Appointment appointment = appointment(UUID.randomUUID(), UUID.randomUUID(),
                FUTURE, AppointmentStatus.SCHEDULED);
        when(administrativeAppointmentRepository.findDetailedById(appointment.getId()))
                .thenReturn(Optional.of(appointment));

        var response = service.findById(appointment.getId());

        assertThat(response.id()).isEqualTo(appointment.getId());
        assertThat(response.patient().name()).isEqualTo("Paciente Agenda");

        UUID missingId = UUID.randomUUID();
        when(administrativeAppointmentRepository.findDetailedById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(missingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment not found");
    }

    @Test
    void createsAppointmentThroughExistingDomainService() {
        UUID patientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        Appointment appointment = appointment(patientId, professionalId, FUTURE, AppointmentStatus.SCHEDULED);
        when(appointmentService.create(patientId, professionalId, FUTURE)).thenReturn(appointment);

        var response = service.create(new CreateAdministrativeAppointmentRequest(
                patientId, professionalId, FUTURE));

        assertThat(response.id()).isEqualTo(appointment.getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(appointmentService).create(patientId, professionalId, FUTURE);
    }

    @Test
    void creationPreservesExistingPatientProfessionalAndScheduleErrors() {
        UUID patientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        var request = new CreateAdministrativeAppointmentRequest(patientId, professionalId, FUTURE);

        when(appointmentService.create(patientId, professionalId, FUTURE))
                .thenThrow(new ResourceNotFoundException("Patient not found"))
                .thenThrow(new ResourceNotFoundException("Professional not found"))
                .thenThrow(new ConflictException("Appointment time is not available"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Patient not found");
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Professional not found");
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class).hasMessage("Appointment time is not available");
    }

    @Test
    void reschedulesScheduledAppointmentAndUpdatesTimestamp() {
        Appointment appointment = appointment(UUID.randomUUID(), UUID.randomUUID(),
                FUTURE, AppointmentStatus.SCHEDULED);
        Instant newDate = FUTURE.plusSeconds(7200);
        when(administrativeAppointmentRepository.findDetailedByIdForUpdate(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(appointmentService.reschedule(appointment, newDate)).thenAnswer(invocation -> {
            appointment.setScheduledAt(newDate);
            appointment.setUpdatedAt(NOW);
            return appointment;
        });

        var response = service.reschedule(appointment.getId(), newDate);

        assertThat(response.scheduledAt()).isEqualTo(newDate);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        verify(appointmentService).reschedule(appointment, newDate);
    }

    @Test
    void rejectsInvalidOrConflictingReschedule() {
        Appointment completed = appointment(UUID.randomUUID(), UUID.randomUUID(),
                FUTURE, AppointmentStatus.COMPLETED);
        when(administrativeAppointmentRepository.findDetailedByIdForUpdate(completed.getId()))
                .thenReturn(Optional.of(completed));
        when(appointmentService.reschedule(completed, FUTURE.plusSeconds(1)))
                .thenThrow(new ConflictException("Only scheduled appointments can be rescheduled"));

        assertThatThrownBy(() -> service.reschedule(completed.getId(), FUTURE.plusSeconds(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only scheduled appointments can be rescheduled");

        Appointment scheduled = appointment(UUID.randomUUID(), UUID.randomUUID(),
                FUTURE, AppointmentStatus.SCHEDULED);
        Instant occupiedDate = FUTURE.plusSeconds(7200);
        when(administrativeAppointmentRepository.findDetailedByIdForUpdate(scheduled.getId()))
                .thenReturn(Optional.of(scheduled));
        when(appointmentService.reschedule(scheduled, occupiedDate))
                .thenThrow(new ConflictException("Appointment time is not available"));

        assertThatThrownBy(() -> service.reschedule(scheduled.getId(), occupiedDate))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Appointment time is not available");
        verify(administrativeAppointmentRepository, never()).saveAndFlush(scheduled);
    }

    @Test
    void completesScheduledAppointment() {
        Appointment scheduled = appointment(UUID.randomUUID(), UUID.randomUUID(),
                FUTURE, AppointmentStatus.SCHEDULED);
        when(administrativeAppointmentRepository.findDetailedByIdForUpdate(scheduled.getId()))
                .thenReturn(Optional.of(scheduled));
        when(administrativeAppointmentRepository.saveAndFlush(scheduled)).thenReturn(scheduled);

        var completed = service.updateStatus(scheduled.getId(), AppointmentStatus.COMPLETED);

        assertThat(completed.status()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(completed.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void delegatesAdministrativeCancellationToSharedDomainRule() {
        Appointment scheduled = appointment(UUID.randomUUID(), UUID.randomUUID(),
                FUTURE, AppointmentStatus.SCHEDULED);
        when(administrativeAppointmentRepository.findDetailedByIdForUpdate(scheduled.getId()))
                .thenReturn(Optional.of(scheduled));
        when(appointmentService.cancel(scheduled)).thenAnswer(invocation -> {
            scheduled.setStatus(AppointmentStatus.CANCELLED);
            scheduled.setUpdatedAt(NOW);
            return scheduled;
        });

        var cancelled = service.updateStatus(scheduled.getId(), AppointmentStatus.CANCELLED);

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.updatedAt()).isEqualTo(NOW);
        verify(appointmentService).cancel(scheduled);
        verify(administrativeAppointmentRepository, never()).saveAndFlush(scheduled);
    }

    @Test
    void administrativeCancellationPreservesSharedInvalidTransitionRule() {
        Appointment completed = appointment(UUID.randomUUID(), UUID.randomUUID(),
                FUTURE, AppointmentStatus.COMPLETED);
        when(administrativeAppointmentRepository.findDetailedByIdForUpdate(completed.getId()))
                .thenReturn(Optional.of(completed));
        when(appointmentService.cancel(completed))
                .thenThrow(new ConflictException("Only scheduled appointments can be cancelled"));

        assertThatThrownBy(() -> service.updateStatus(completed.getId(), AppointmentStatus.CANCELLED))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only scheduled appointments can be cancelled");
    }

    @Test
    void rejectsSameStatusTransition() {
        Appointment completed = appointment(UUID.randomUUID(), UUID.randomUUID(),
                FUTURE, AppointmentStatus.COMPLETED);
        when(administrativeAppointmentRepository.findDetailedByIdForUpdate(completed.getId()))
                .thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.updateStatus(completed.getId(), AppointmentStatus.COMPLETED))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Appointment already has the requested status");
    }

    @Test
    void capsPageSizeAtOneHundred() {
        when(administrativeAppointmentRepository.findAll(
                ArgumentMatchers.<Specification<Appointment>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.findAll(null, null, null, null, null, 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(administrativeAppointmentRepository).findAll(
                ArgumentMatchers.<Specification<Appointment>>any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    private Appointment appointment(UUID patientId, UUID professionalId,
                                    Instant scheduledAt, AppointmentStatus status) {
        Patient patient = new Patient();
        patient.setId(patientId);
        patient.setCode("PAC-001");
        patient.setName("Paciente Agenda");
        patient.setPhone("5555-0101");

        User professional = new User();
        professional.setId(professionalId);
        professional.setFullName("Dra. Andrea Ruiz");
        professional.setStatus(UserStatus.ACTIVE);

        return new Appointment(UUID.randomUUID(), patient, professional, scheduledAt,
                status, NOW.minusSeconds(3600), NOW.minusSeconds(3600));
    }
}
