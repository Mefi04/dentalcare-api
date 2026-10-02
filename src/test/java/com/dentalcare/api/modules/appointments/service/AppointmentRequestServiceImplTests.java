package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.appointments.mapper.AppointmentRequestMapper;
import com.dentalcare.api.modules.appointments.model.*;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.*;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentRequestServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");
    private static final Instant FUTURE = NOW.plusSeconds(86_400);

    @Mock AppointmentRequestRepository requests;
    @Mock PatientRepository patients;
    @Mock UserRepository users;
    @Mock AppointmentService appointments;

    private AppointmentRequestServiceImpl service;
    private UUID patientUserId;
    private Patient patient;
    private User dentist;
    private User secretary;

    @BeforeEach
    void setUp() {
        service = new AppointmentRequestServiceImpl(requests, patients, users, appointments,
                new AppointmentRequestMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        patientUserId = UUID.randomUUID();
        patient = patient();
        dentist = user("DENTIST");
        secretary = user("SECRETARY");
    }

    @Test
    void patientCreatesPendingRequestWithoutCreatingAppointment() {
        when(patients.findByUser_Id(patientUserId)).thenReturn(Optional.of(patient));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createForPatient(patientUserId, dentist.getId(), FUTURE);

        assertThat(result.status()).isEqualTo(AppointmentRequestStatus.PENDING);
        assertThat(result.actionRequiredBy()).isEqualTo("CLINIC");
        assertThat(result.appointmentId()).isNull();
        verifyNoInteractions(appointments);
    }

    @Test
    void clinicAcceptsPendingRequestExactlyOnceAndRetryIsIdempotent() {
        AppointmentRequest request = request(AppointmentRequestStatus.PENDING);
        Appointment appointment = appointment();
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(appointments.create(patient.getId(), dentist.getId(), FUTURE)).thenReturn(appointment);
        when(requests.saveAndFlush(request)).thenReturn(request);

        var first = service.acceptRequestedTime(secretary.getId(), request.getId());
        var retry = service.acceptRequestedTime(secretary.getId(), request.getId());

        assertThat(first.status()).isEqualTo(AppointmentRequestStatus.CONFIRMED);
        assertThat(retry.appointmentId()).isEqualTo(appointment.getId());
        verify(appointments, times(1)).create(patient.getId(), dentist.getId(), FUTURE);
    }

    @Test
    void proposalCanOnlyBeAcceptedByOwningPatient() {
        AppointmentRequest request = request(AppointmentRequestStatus.PENDING);
        request.propose(dentist, FUTURE.plusSeconds(3600), secretary, NOW);
        when(patients.findByUser_Id(patientUserId)).thenReturn(Optional.of(patient));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        Appointment appointment = appointment();
        when(appointments.create(patient.getId(), dentist.getId(), FUTURE.plusSeconds(3600)))
                .thenReturn(appointment);
        when(requests.saveAndFlush(request)).thenReturn(request);

        var accepted = service.acceptProposal(patientUserId, request.getId());

        assertThat(accepted.status()).isEqualTo(AppointmentRequestStatus.CONFIRMED);
        assertThat(accepted.appointmentId()).isEqualTo(appointment.getId());

        Patient anotherPatient = patient();
        UUID anotherUser = UUID.randomUUID();
        AppointmentRequest foreign = request(AppointmentRequestStatus.PENDING);
        when(patients.findByUser_Id(anotherUser)).thenReturn(Optional.of(anotherPatient));
        when(requests.findDetailedByIdForUpdate(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.cancelForPatient(anotherUser, foreign.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment request not found");
    }

    @Test
    void closedRequestsCannotBeReprocessedAndPastDatesAreRejected() {
        AppointmentRequest request = request(AppointmentRequestStatus.PENDING);
        request.reject(secretary, NOW);
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.propose(secretary.getId(), request.getId(), null, FUTURE))
                .isInstanceOf(ConflictException.class);

        when(patients.findByUser_Id(patientUserId)).thenReturn(Optional.of(patient));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        assertThatThrownBy(() -> service.createForPatient(patientUserId, dentist.getId(), NOW))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be in the future");
    }

    private AppointmentRequest request(AppointmentRequestStatus status) {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, dentist, FUTURE,
                AppointmentRequestStatus.PENDING, NOW, NOW);
        if (status == AppointmentRequestStatus.REJECTED) request.reject(secretary, NOW);
        return request;
    }

    private Appointment appointment() {
        return new Appointment(UUID.randomUUID(), patient, dentist, FUTURE,
                AppointmentStatus.SCHEDULED, NOW, NOW);
    }

    private Patient patient() {
        Patient value = new Patient();
        value.setId(UUID.randomUUID());
        value.setCode("PAC-001");
        value.setName("Ana Pérez");
        value.setPhone("5555-0101");
        return value;
    }

    private User user(String roleCode) {
        User value = new User(UUID.randomUUID(), roleCode.toLowerCase() + UUID.randomUUID(), roleCode,
                roleCode.toLowerCase() + "@example.test", "1234567890123", "hash", UserStatus.ACTIVE, NOW, NOW);
        value.setRoles(Set.of(new Role(UUID.randomUUID(), roleCode, roleCode, null, true)));
        return value;
    }
}
