package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.appointments.mapper.AppointmentRequestMapper;
import com.dentalcare.api.modules.appointments.dto.request.CreatePublicAppointmentRequest;
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
    @Mock com.dentalcare.api.modules.appointments.repository.AppointmentContactAttemptRepository contactAttempts;
    @Mock AppointmentAvailabilityService availability;

    private AppointmentRequestServiceImpl service;
    private UUID patientUserId;
    private Patient patient;
    private User dentist;
    private User secretary;

    @BeforeEach
    void setUp() {
        service = new AppointmentRequestServiceImpl(requests, patients, users, appointments,
                new AppointmentRequestMapper(), Clock.fixed(NOW, ZoneOffset.UTC), contactAttempts, availability);
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
    void publicRequestDoesNotAutoLinkPatientAndOnlyAcknowledgesPublicly() {
        UUID idempotencyKey = UUID.randomUUID();
        var request = publicRequest(dentist.getId());
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var receipt = service.createPublic(request, idempotencyKey);

        ArgumentCaptor<AppointmentRequest> captor = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(requests).saveAndFlush(captor.capture());
        AppointmentRequest persisted = captor.getValue();
        assertThat(persisted.getPatient()).isNull();
        assertThat(persisted.getSource()).isEqualTo(AppointmentRequestSource.PUBLIC);
        assertThat(persisted.getStatus()).isEqualTo(AppointmentRequestStatus.PENDING);
        assertThat(persisted.isPrivacyAccepted()).isTrue();
        assertThat(persisted.getRequesterCui()).isEqualTo("1234567890123");
        assertThat(persisted.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(receipt.requestId()).isEqualTo(persisted.getId());
        assertThat(receipt.message()).doesNotContain("1234567890123", "maria@example.test", "5555-0101");
        verifyNoInteractions(appointments, patients);

        when(requests.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(persisted));
        var retry = service.createPublic(request, idempotencyKey);
        assertThat(retry).isEqualTo(receipt);
        verify(requests, times(1)).saveAndFlush(any());
    }

    @Test
    void publicRequestWithoutMatchingPatientRemainsUnlinkedAndDoesNotCreateUserOrPatient() {
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPublic(publicRequest(null, "9999999999999"), UUID.randomUUID());

        ArgumentCaptor<AppointmentRequest> captor = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(requests).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPatient()).isNull();
        assertThat(captor.getValue().getRequestedProfessional()).isNull();
        verifyNoInteractions(patients, users, appointments);
    }

    @Test
    void rejectsPublicRequestWhenPrivacyNoticeWasNotAccepted() {
        var base = publicRequest(null);
        var request = new CreatePublicAppointmentRequest(base.fullName(), base.cui(), base.phone(), base.email(),
                base.requestedAt(), base.professionalId(), base.reason(), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, false, "1.0");
        assertThatThrownBy(() -> service.createPublic(request, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Privacy notice");
        verifyNoInteractions(requests);
    }

    @Test
    void receptionistCanAssignActiveDentistToPublicRequestWithoutConfirmingIt() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, null, FUTURE,
                AppointmentRequestStatus.PENDING, NOW, NOW, "Maria Lopez", null, "5555-0101",
                null, null, UUID.randomUUID(), "hash");
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(requests.saveAndFlush(request)).thenReturn(request);

        var assigned = service.assignPublicRequestProfessional(secretary.getId(), request.getId(), dentist.getId());

        assertThat(assigned.status()).isEqualTo(AppointmentRequestStatus.PENDING);
        assertThat(assigned.source()).isEqualTo("PUBLIC");
        assertThat(assigned.contact().fullName()).isEqualTo("Maria Lopez");
        assertThat(assigned.contact().phone()).isEqualTo("5555-0101");
        assertThat(assigned.contact().cui()).isNull();
        assertThat(assigned.requestedProfessional()).isNull();
        assertThat(assigned.assignedProfessional().id()).isEqualTo(dentist.getId());
        assertThat(assigned.appointmentId()).isNull();
        assertThat(request.getAssignedProfessional()).isSameAs(dentist);
        assertThat(request.getRequestedProfessional()).isNull();
        verifyNoInteractions(appointments);
    }

    @Test
    void receptionistCanChangeAssignmentOnProposedPublicRequestWithoutConfirmingIt() {
        User reassignedDentist = user("DENTIST");
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, dentist, FUTURE,
                AppointmentRequestStatus.PENDING, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.assignProfessional(dentist, NOW);
        request.propose(dentist, FUTURE.plusSeconds(3600), secretary, NOW.plusSeconds(1));
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(users.findWithRolesById(reassignedDentist.getId())).thenReturn(Optional.of(reassignedDentist));
        when(requests.saveAndFlush(request)).thenReturn(request);

        var reassigned = service.assignPublicRequestProfessional(
                secretary.getId(), request.getId(), reassignedDentist.getId());

        assertThat(reassigned.status()).isEqualTo(AppointmentRequestStatus.PROPOSED);
        assertThat(reassigned.assignedProfessional().id()).isEqualTo(reassignedDentist.getId());
        assertThat(reassigned.requestedProfessional().id()).isEqualTo(dentist.getId());
        assertThat(reassigned.proposedProfessional().id()).isEqualTo(dentist.getId());
        assertThat(reassigned.appointmentId()).isNull();
        verifyNoInteractions(appointments);
    }

    @Test
    void assignmentConflictsExplainNonPublicStateAndUnavailableDentist() {
        AppointmentRequest patientRequest = request(AppointmentRequestStatus.PENDING);
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(patientRequest.getId())).thenReturn(Optional.of(patientRequest));
        assertThatThrownBy(() -> service.assignPublicRequestProfessional(
                secretary.getId(), patientRequest.getId(), dentist.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_REQUEST_NOT_PUBLIC");

        AppointmentRequest closedPublic = new AppointmentRequest(UUID.randomUUID(), null, null, FUTURE,
                AppointmentRequestStatus.CONFIRMED, NOW, NOW, "Maria Lopez", null,
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        when(requests.findDetailedByIdForUpdate(closedPublic.getId())).thenReturn(Optional.of(closedPublic));
        assertThatThrownBy(() -> service.assignPublicRequestProfessional(
                secretary.getId(), closedPublic.getId(), dentist.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_REQUEST_STATE_NOT_ELIGIBLE")
                .hasMessageContaining("CONFIRMED")
                .hasMessageContaining("PENDING and PROPOSED");

        AppointmentRequest pendingPublic = new AppointmentRequest(UUID.randomUUID(), null, null, FUTURE,
                AppointmentRequestStatus.PENDING, NOW, NOW, "Maria Lopez", null,
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        when(requests.findDetailedByIdForUpdate(pendingPublic.getId())).thenReturn(Optional.of(pendingPublic));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(user("ASSISTANT")));
        assertThatThrownBy(() -> service.assignPublicRequestProfessional(
                secretary.getId(), pendingPublic.getId(), dentist.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PROFESSIONAL_NOT_AVAILABLE");
    }

    @Test
    void publicConfirmationDistinguishesOccupiedTimeFromIneligibleState() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, dentist, FUTURE,
                AppointmentRequestStatus.PENDING, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.propose(dentist, FUTURE.plusSeconds(3600), secretary, NOW);
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(appointments.create(patient.getId(), dentist.getId(), FUTURE.plusSeconds(3600)))
                .thenThrow(new ConflictException("Appointment time is not available"));

        assertThatThrownBy(() -> service.confirmPublicProposal(secretary.getId(), request.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_TIME_UNAVAILABLE")
                .hasMessageContaining("already occupied");

        request.reject(secretary, NOW);
        assertThatThrownBy(() -> service.confirmPublicProposal(secretary.getId(), request.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_REQUEST_STATE_NOT_ELIGIBLE")
                .hasMessageContaining("REJECTED");
    }

    @Test
    void directPublicConfirmationRequiresContactedAndCreatesAppointmentAtomically() {
        Instant agreedAt = FUTURE.plusSeconds(7200);
        UUID requestId = UUID.randomUUID();
        AppointmentRequest request = new AppointmentRequest(requestId, null, dentist, FUTURE,
                AppointmentRequestStatus.PENDING, NOW, NOW, "Maria Lopez", null,
                "5555-0101", "maria@example.test", "First visit", UUID.randomUUID(), "hash");
        var command = new com.dentalcare.api.modules.appointments.dto.request.ConfirmPublicAppointmentRequest(
                dentist.getId(), agreedAt);
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirmPublicAppointment(secretary.getId(), requestId, command))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_CONTACT_REQUIRED");
        verify(appointments, never()).createPublic(any(), any(), any(), any());

        when(contactAttempts.existsByRequest_IdAndResult(requestId, AppointmentContactResult.CONTACTED)).thenReturn(true);
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(availability.isSlotAvailableForBooking(agreedAt, dentist.getId())).thenReturn(false, true);
        assertThatThrownBy(() -> service.confirmPublicAppointment(secretary.getId(), requestId, command))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_TIME_UNAVAILABLE");
        verify(appointments, never()).createPublic(any(), any(), any(), any());

        Appointment created = new Appointment(UUID.randomUUID(), dentist, agreedAt,
                AppointmentStatus.SCHEDULED, "Maria Lopez", "5555-0101", NOW, NOW);
        when(appointments.createPublic("Maria Lopez", "5555-0101", dentist.getId(), agreedAt)).thenReturn(created);
        when(requests.saveAndFlush(request)).thenReturn(request);

        var response = service.confirmPublicAppointment(secretary.getId(), requestId, command);

        assertThat(response.status()).isEqualTo(AppointmentRequestStatus.CONFIRMED);
        assertThat(response.appointmentId()).isEqualTo(created.getId());
        assertThat(response.proposedAt()).isEqualTo(agreedAt);
        verify(appointments, times(1)).createPublic("Maria Lopez", "5555-0101", dentist.getId(), agreedAt);
    }

    @Test
    void directPublicConfirmationRejectsPatientPortalRequest() {
        AppointmentRequest request = request(AppointmentRequestStatus.PENDING);
        var command = new com.dentalcare.api.modules.appointments.dto.request.ConfirmPublicAppointmentRequest(
                dentist.getId(), FUTURE);
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.confirmPublicAppointment(secretary.getId(), request.getId(), command))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_REQUEST_NOT_PUBLIC");
    }

    @Test
    void publicRequestRejectsEquivalentActiveRequestAndInactivePreferredProfessional() {
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(requests.existsByRequesterCuiAndRequestedAtAndRequestedProfessional_IdAndStatusIn(
                "1234567890123", FUTURE, dentist.getId(), List.of(
                        AppointmentRequestStatus.PENDING, AppointmentRequestStatus.PROPOSED))).thenReturn(true);
        assertThatThrownBy(() -> service.createPublic(publicRequest(dentist.getId()), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An equivalent appointment request is already active");

        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(user("ASSISTANT")));
        assertThatThrownBy(() -> service.createPublic(publicRequest(dentist.getId()), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Professional is not an active dentist");
    }

    @Test
    void publicRequestRejectsReusingIdempotencyKeyForDifferentPayloadAndPastDate() {
        AppointmentRequest existing = new AppointmentRequest(UUID.randomUUID(), patient, dentist, FUTURE,
                AppointmentRequestStatus.PENDING, NOW, NOW, "Maria Lopez", "1234567890123", "5555-0101",
                "maria@example.test", "First visit", UUID.randomUUID(), "different-hash");
        UUID key = existing.getIdempotencyKey();
        when(requests.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.createPublic(publicRequest(dentist.getId()), key))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Idempotency key");

        assertThatThrownBy(() -> service.createPublic(publicRequest(dentist.getId(), "1234567890123", NOW), UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be in the future");
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
        when(availability.isSlotAvailableForBooking(FUTURE.plusSeconds(3600), dentist.getId())).thenReturn(true);
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
    void patientCannotAcceptProposalWhenSlotBecameUnavailable() {
        AppointmentRequest request = request(AppointmentRequestStatus.PENDING);
        request.propose(dentist, FUTURE.plusSeconds(3600), secretary, NOW);
        when(patients.findByUser_Id(patientUserId)).thenReturn(Optional.of(patient));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(availability.isSlotAvailableForBooking(FUTURE.plusSeconds(3600), dentist.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.acceptProposal(patientUserId, request.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("refresh availability")
                .extracting("code").isEqualTo("APPOINTMENT_TIME_UNAVAILABLE");
        verifyNoInteractions(appointments);
    }

    @Test
    void secretaryCannotSendProposalForUnbookableTime() {
        AppointmentRequest request = request(AppointmentRequestStatus.PENDING);
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(availability.isSlotAvailableForBooking(FUTURE, dentist.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.propose(secretary.getId(), request.getId(), dentist.getId(), FUTURE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not a bookable slot")
                .extracting("code").isEqualTo("APPOINTMENT_TIME_UNAVAILABLE");
        verify(requests, never()).saveAndFlush(any());
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

    @Test
    void publicRequestConfirmedWithoutLinkedPatientCreatesPublicAppointment() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, dentist, FUTURE,
                AppointmentRequestStatus.PENDING, NOW, NOW, "Maria Lopez", null, "5555-0101",
                "maria@example.test", "First visit", UUID.randomUUID(), "hash");
        request.setSource(AppointmentRequestSource.PUBLIC);

        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        Appointment publicAppointment = new Appointment(UUID.randomUUID(), null, dentist, FUTURE,
                AppointmentStatus.SCHEDULED, NOW, NOW);
        when(appointments.createPublic("Maria Lopez", "5555-0101", dentist.getId(), FUTURE))
                .thenReturn(publicAppointment);
        when(requests.saveAndFlush(request)).thenReturn(request);

        var response = service.acceptRequestedTime(secretary.getId(), request.getId());

        assertThat(response.status()).isEqualTo(AppointmentRequestStatus.CONFIRMED);
        assertThat(response.appointmentId()).isEqualTo(publicAppointment.getId());
        verify(appointments).createPublic("Maria Lopez", "5555-0101", dentist.getId(), FUTURE);
        verify(appointments, never()).create(any(), any(), any());
    }

    @Test
    void receptionistLogsContactAttemptSuccessfully() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, dentist, FUTURE,
                AppointmentRequestStatus.PENDING, NOW, NOW, "Maria Lopez", null, "5555-0101",
                "maria@example.test", "First visit", UUID.randomUUID(), "hash");

        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(contactAttempts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var req = new com.dentalcare.api.modules.appointments.dto.request.CreateContactAttemptRequest(
                AppointmentContactResult.NO_ANSWER, "Llamada sin respuesta"
        );

        var result = service.logContactAttempt(secretary.getId(), request.getId(), req);

        assertThat(result.result()).isEqualTo(AppointmentContactResult.NO_ANSWER);
        assertThat(result.notes()).isEqualTo("Llamada sin respuesta");
        verify(contactAttempts).saveAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllFiltersBySourceCorrectly() {
        AppointmentRequest r1 = request(AppointmentRequestStatus.PENDING);
        r1.setSource(AppointmentRequestSource.PUBLIC);
        when(requests.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(r1)));

        var results = service.findAll(null, null, null, null,
                AppointmentRequestStatus.PENDING, AppointmentRequestSource.PUBLIC, 0, 10);

        assertThat(results.getContent()).hasSize(1);
        verify(requests).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
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

    private CreatePublicAppointmentRequest publicRequest(UUID professionalId) {
        return publicRequest(professionalId, "1234567890123", FUTURE);
    }

    private CreatePublicAppointmentRequest publicRequest(UUID professionalId, String cui) {
        return publicRequest(professionalId, cui, FUTURE);
    }

    private CreatePublicAppointmentRequest publicRequest(UUID professionalId, String cui, Instant requestedAt) {
        return new CreatePublicAppointmentRequest("Maria Lopez", cui, "5555-0101", "maria@example.test",
                requestedAt, professionalId, "First visit");
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
