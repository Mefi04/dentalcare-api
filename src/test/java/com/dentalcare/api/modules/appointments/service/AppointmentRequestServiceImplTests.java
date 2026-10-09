package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.appointments.mapper.AppointmentRequestMapper;
import com.dentalcare.api.modules.appointments.dto.request.CreatePublicAppointmentRequest;
import com.dentalcare.api.modules.appointments.model.*;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentPublicConversationRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestMessageRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.*;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock AppointmentPublicConversationRepository conversations;
    @Mock AppointmentRequestMessageRepository messages;
    @Mock PublicAppointmentCodeDelivery codeDelivery;
    @Mock PasswordEncoder passwordEncoder;
    @Mock com.dentalcare.api.modules.appointments.repository.AppointmentRepository appointmentRepository;

    private AppointmentRequestServiceImpl service;
    private UUID patientUserId;
    private Patient patient;
    private User dentist;
    private User secretary;

    @BeforeEach
    void setUp() {
        service = new AppointmentRequestServiceImpl(requests, patients, users, appointments,
                new AppointmentRequestMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "conversations", conversations);
        ReflectionTestUtils.setField(service, "messages", messages);
        ReflectionTestUtils.setField(service, "codeDelivery", codeDelivery);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "appointmentRepository", appointmentRepository);
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
    void publicRequestLinksExistingPatientButOnlyAcknowledgesPublicly() {
        UUID idempotencyKey = UUID.randomUUID();
        var request = publicRequest(dentist.getId());
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(patients.findByDpi("1234567890123")).thenReturn(Optional.of(patient));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var receipt = service.createPublic(request, idempotencyKey);

        ArgumentCaptor<AppointmentRequest> captor = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(requests).saveAndFlush(captor.capture());
        AppointmentRequest persisted = captor.getValue();
        assertThat(persisted.getPatient()).isSameAs(patient);
        assertThat(persisted.getStatus()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
        assertThat(persisted.getRequesterCui()).isEqualTo("1234567890123");
        assertThat(persisted.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(receipt.requestId()).isEqualTo(persisted.getId());
        assertThat(receipt.message()).doesNotContain("1234567890123", "maria@example.test", "5555-0101");
        verifyNoInteractions(appointments);

        when(requests.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(persisted));
        var retry = service.createPublic(request, idempotencyKey);
        assertThat(retry).isEqualTo(receipt);
        verify(requests, times(1)).saveAndFlush(any());
    }

    @Test
    void publicRequestWithoutMatchingPatientRemainsUnlinkedAndDoesNotCreateUserOrPatient() {
        when(patients.findByDpi("9999999999999")).thenReturn(Optional.empty());
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPublic(publicRequest(null, "9999999999999"), UUID.randomUUID());

        ArgumentCaptor<AppointmentRequest> captor = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(requests).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPatient()).isNull();
        assertThat(captor.getValue().getRequestedProfessional()).isNull();
        verify(patients).findByDpi("9999999999999");
        verifyNoInteractions(users, appointments);
    }

    @Test
    void receptionistCanAssignActiveDentistToPublicRequestWithoutConfirmingIt() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", null, "5555-0101",
                null, null, UUID.randomUUID(), "hash");
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(requests.saveAndFlush(request)).thenReturn(request);

        var assigned = service.assignPublicRequestProfessional(secretary.getId(), request.getId(), dentist.getId());

        assertThat(assigned.status()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
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
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.assignProfessional(dentist, NOW);
        request.propose(dentist, FUTURE.plusSeconds(3600), secretary, NOW.plusSeconds(1));
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(users.findWithRolesById(reassignedDentist.getId())).thenReturn(Optional.of(reassignedDentist));
        when(requests.saveAndFlush(request)).thenReturn(request);

        var reassigned = service.assignPublicRequestProfessional(
                secretary.getId(), request.getId(), reassignedDentist.getId());

        assertThat(reassigned.status()).isEqualTo(AppointmentRequestStatus.PENDING_PATIENT);
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
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", null,
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        when(requests.findDetailedByIdForUpdate(pendingPublic.getId())).thenReturn(Optional.of(pendingPublic));
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(user("ASSISTANT")));
        assertThatThrownBy(() -> service.assignPublicRequestProfessional(
                secretary.getId(), pendingPublic.getId(), dentist.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PROFESSIONAL_NOT_AVAILABLE");
    }

    @Test
    void publicConfirmationCannotBypassVerifiedRequesterDecision() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, dentist, FUTURE,
                AppointmentRequestStatus.PENDING_PATIENT, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.propose(dentist, FUTURE.plusSeconds(3600), secretary, NOW);
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.confirmPublicProposal(secretary.getId(), request.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PUBLIC_PATIENT_ACCEPTANCE_REQUIRED");

        verifyNoInteractions(appointments);
    }

    @Test
    void publicRequestRejectsEquivalentActiveRequestAndInactivePreferredProfessional() {
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(requests.existsByRequesterCuiAndRequestedAtAndRequestedProfessional_IdAndStatusIn(
                "1234567890123", FUTURE, dentist.getId(), List.of(
                        AppointmentRequestStatus.PENDING_CLINIC, AppointmentRequestStatus.PENDING_PATIENT))).thenReturn(true);
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
    void publicRequestRejectsClinicalDetailsInLogisticalReason() {
        CreatePublicAppointmentRequest request = new CreatePublicAppointmentRequest("Maria Lopez", null,
                "5555-0101", null, FUTURE, null, "Tengo dolor y sangrado");
        assertThatThrownBy(() -> service.createPublic(request, UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("scheduling information only");
        verifyNoInteractions(requests, patients, users, appointments);
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

    @Test
    void verifiedPublicAcceptanceCreatesAppointmentOnlyAfterReceptionLinksPatient() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.assignProfessional(dentist, NOW);
        request.propose(dentist, FUTURE, NOW.plusSeconds(3600), secretary, NOW);
        String rawToken = "random-unpredictable-conversation-token";
        AppointmentPublicConversation conversation = verifiedConversation(request.getId(), rawToken);
        when(conversations.findByConversationTokenHash(anyString())).thenReturn(Optional.of(conversation));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(appointments.create(patient.getId(), dentist.getId(), FUTURE)).thenReturn(appointment());
        when(requests.saveAndFlush(request)).thenReturn(request);

        var accepted = service.decidePublicProposal(request.getId(), rawToken,
                new com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest(
                        com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest.Decision.ACCEPT),
                UUID.randomUUID());

        assertThat(accepted.status()).isEqualTo(AppointmentRequestStatus.CONFIRMED);
        assertThat(accepted.appointmentId()).isNotNull();
        verify(appointments).create(patient.getId(), dentist.getId(), FUTURE);
    }

    @Test
    void acceptingWithoutReceptionLinkedPatientReturnsConflictAndDoesNotCreateAppointment() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", null,
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.assignProfessional(dentist, NOW);
        request.propose(dentist, FUTURE, NOW.plusSeconds(3600), secretary, NOW);
        String rawToken = "scoped-conversation-token";
        when(conversations.findByConversationTokenHash(anyString()))
                .thenReturn(Optional.of(verifiedConversation(request.getId(), rawToken)));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.decidePublicProposal(request.getId(), rawToken,
                new com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest(
                        com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest.Decision.ACCEPT),
                UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_RECORD_LINK_REQUIRED");
        verifyNoInteractions(appointments);
    }

    @Test
    void publicRejectionReturnsRequestToReceptionAndExpiredProposalCanBeReplaced() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.propose(dentist, FUTURE, NOW.plusSeconds(3600), secretary, NOW);
        String rawToken = "scoped-conversation-token";
        when(conversations.findByConversationTokenHash(anyString()))
                .thenReturn(Optional.of(verifiedConversation(request.getId(), rawToken)));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);

        var rejected = service.decidePublicProposal(request.getId(), rawToken,
                new com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest(
                        com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest.Decision.REJECT),
                UUID.randomUUID());

        assertThat(rejected.status()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
        verifyNoInteractions(appointments);
    }

    @Test
    void conversationTokenCannotReadAnotherRequestAndOtpAttemptsAreLimited() {
        UUID requestId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        AppointmentPublicConversation otherConversation = verifiedConversation(otherId, "not-this-request-token");
        when(conversations.findByConversationTokenHash(anyString())).thenReturn(Optional.of(otherConversation));
        assertThatThrownBy(() -> service.getPublicConversation(requestId, "not-this-request-token"))
                .isInstanceOf(UnauthorizedException.class);

        AppointmentPublicConversation otp = new AppointmentPublicConversation(requestId, "SMS", "hash",
                FUTURE, NOW);
        when(conversations.findForUpdate(requestId)).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("000000", "hash")).thenReturn(false);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.verifyConversationCode(requestId,
                    new com.dentalcare.api.modules.appointments.dto.request.VerifyPublicAppointmentCodeRequest("000000")))
                    .isInstanceOf(UnauthorizedException.class);
        }
        assertThatThrownBy(() -> service.verifyConversationCode(requestId,
                new com.dentalcare.api.modules.appointments.dto.request.VerifyPublicAppointmentCodeRequest("000000")))
                .isInstanceOf(com.dentalcare.api.security.ratelimit.RateLimitExceededException.class);
        assertThat(otp.getVerificationAttempts()).isEqualTo(5);
    }

    @Test
    void successfulOtpIsConsumedAndReturnsOnlyAHashedShortLivedConversationToken() {
        UUID requestId = UUID.randomUUID();
        AppointmentPublicConversation otp = new AppointmentPublicConversation(requestId, "EMAIL", "bcrypt-code",
                FUTURE, NOW);
        when(conversations.findForUpdate(requestId)).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("482193", "bcrypt-code")).thenReturn(true);
        when(conversations.saveAndFlush(otp)).thenReturn(otp);

        var result = service.verifyConversationCode(requestId,
                new com.dentalcare.api.modules.appointments.dto.request.VerifyPublicAppointmentCodeRequest("482193"));

        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.conversationToken()).hasSizeGreaterThan(40);
        assertThat(otp.getVerificationCodeHash()).isNull();
        assertThat(otp.getConversationTokenHash()).isEqualTo(tokenHash(result.conversationToken()))
                .isNotEqualTo(result.conversationToken());
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void expiredProposalRemainsOpenAndReturns410ForAnotherClinicProposal() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.propose(dentist, FUTURE, NOW.minusSeconds(1), secretary, NOW);
        String rawToken = "scoped-conversation-token";
        when(conversations.findByConversationTokenHash(anyString()))
                .thenReturn(Optional.of(verifiedConversation(request.getId(), rawToken)));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);

        assertThatThrownBy(() -> service.decidePublicProposal(request.getId(), rawToken,
                new com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest(
                        com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest.Decision.ACCEPT),
                UUID.randomUUID()))
                .isInstanceOf(GoneException.class);
        assertThat(request.getStatus()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
        assertThat(request.getProposedAt()).isNull();
        verifyNoInteractions(appointments);
    }

    @Test
    void unavailableSlotReturns409WithoutChangingRequestOrCreatingAppointment() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.propose(dentist, FUTURE, NOW.plusSeconds(3600), secretary, NOW);
        String rawToken = "scoped-conversation-token";
        when(conversations.findByConversationTokenHash(anyString()))
                .thenReturn(Optional.of(verifiedConversation(request.getId(), rawToken)));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(appointments.create(patient.getId(), dentist.getId(), FUTURE))
                .thenThrow(new ConflictException("Appointment time is not available"));

        assertThatThrownBy(() -> service.decidePublicProposal(request.getId(), rawToken,
                new com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest(
                        com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest.Decision.ACCEPT),
                UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_TIME_UNAVAILABLE");
        assertThat(request.getStatus()).isEqualTo(AppointmentRequestStatus.PENDING_PATIENT);
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

    private AppointmentPublicConversation verifiedConversation(UUID requestId, String rawToken) {
        AppointmentPublicConversation value = new AppointmentPublicConversation(requestId, "SMS", null, null, NOW);
        value.consumeCode(tokenHash(rawToken), FUTURE, NOW);
        return value;
    }

    private String tokenHash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
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
