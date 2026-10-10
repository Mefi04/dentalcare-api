package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.appointments.mapper.AppointmentRequestMapper;
import com.dentalcare.api.modules.appointments.dto.request.CreatePublicAppointmentRequest;
import com.dentalcare.api.modules.appointments.model.*;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentPublicConversationRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentPublicDecisionRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestMessageRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.dto.request.CreatePatientRequest;
import com.dentalcare.api.modules.patients.dto.response.PatientResponse;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.patients.service.PatientService;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.users.model.*;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    @Mock com.dentalcare.api.modules.appointments.repository.AppointmentPublicConversationTokenRepository retainedTokens;
    @Mock AppointmentPublicDecisionRepository decisions;
    @Mock AppointmentRequestMessageRepository messages;
    @Mock AppointmentNotificationOutboxService notificationOutbox;
    @Mock AppointmentConversationMessageService conversationMessages;
    @Mock PublicAppointmentCodeDelivery codeDelivery;
    @Mock PasswordEncoder passwordEncoder;
    @Mock com.dentalcare.api.modules.appointments.repository.AppointmentRepository appointmentRepository;
    @Mock PatientService patientService;
    @Mock AuditService auditService;

    private AppointmentRequestServiceImpl service;
    private UUID patientUserId;
    private Patient patient;
    private User dentist;
    private User secretary;

    @BeforeEach
    void setUp() {
        service = new AppointmentRequestServiceImpl(requests, patients, users, appointments,
                new AppointmentRequestMapper(), Clock.fixed(NOW, ZoneOffset.UTC), patientService, auditService,
                conversations, retainedTokens, decisions, messages, notificationOutbox, conversationMessages,
                codeDelivery, passwordEncoder, appointmentRepository);
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
    void publicRequestNeverLooksUpOrLinksPatientByCuiAndOnlyAcknowledgesPublicly() {
        UUID idempotencyKey = UUID.randomUUID();
        var request = publicRequest(dentist.getId());
        java.util.concurrent.atomic.AtomicReference<AppointmentPublicConversation> storedConversation =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(users.findWithRolesById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversations.findForUpdate(any())).thenAnswer(invocation -> Optional.ofNullable(storedConversation.get()));
        when(conversations.saveAndFlush(any())).thenAnswer(invocation -> {
            AppointmentPublicConversation value = invocation.getArgument(0);
            storedConversation.set(value);
            return value;
        });

        var receipt = service.createPublic(request, idempotencyKey);

        ArgumentCaptor<AppointmentRequest> captor = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(requests).saveAndFlush(captor.capture());
        AppointmentRequest persisted = captor.getValue();
        assertThat(persisted.getPatient()).isNull();
        assertThat(persisted.getStatus()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
        assertThat(persisted.getRequesterCui()).isEqualTo("1234567890123");
        assertThat(persisted.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(receipt.requestId()).isEqualTo(persisted.getId());
        assertThat(receipt.conversationToken()).isNotBlank();
        assertThat(receipt.tokenType()).isEqualTo("Bearer");
        assertThat(receipt.conversationExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(storedConversation.get().getChannel()).isEqualTo("WEB");
        assertThat(storedConversation.get().getConversationTokenHash()).isNotEqualTo(receipt.conversationToken());
        assertThat(receipt.message()).doesNotContain("1234567890123", "maria@example.test", "5555-0101");
        verifyNoInteractions(appointments);
        verifyNoInteractions(codeDelivery, notificationOutbox);
        verify(patients, never()).findByDpi(anyString());

        when(requests.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(persisted));
        var retry = service.createPublic(request, idempotencyKey);
        assertThat(retry.requestId()).isEqualTo(receipt.requestId());
        assertThat(retry.conversationToken()).isNotEqualTo(receipt.conversationToken());
        assertThat(storedConversation.get().getConversationTokenHash()).isNotEqualTo(
                tokenHash(receipt.conversationToken()));
        assertThat(storedConversation.get().getConversationTokenHash()).isEqualTo(
                tokenHash(retry.conversationToken()));
        verify(requests, times(1)).saveAndFlush(any());
    }

    @Test
    void publicRequestWithCuiRemainsUnlinkedAndDoesNotQueryPatientOrCreateUser() {
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPublic(publicRequest(null, "9999999999999"), UUID.randomUUID());

        ArgumentCaptor<AppointmentRequest> captor = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(requests).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPatient()).isNull();
        assertThat(captor.getValue().getRequestedProfessional()).isNull();
        verify(patients, never()).findByDpi(anyString());
        verifyNoInteractions(users, appointments);
    }

    @Test
    void publicRequestDoesNotRequireOnlineDpiAndNeverCreatesPatient() {
        CreatePublicAppointmentRequest missingCui = new CreatePublicAppointmentRequest(
                "Maria Lopez", null, "5555-0101", null, FUTURE, null, "First visit");
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var receipt = service.createPublic(missingCui, UUID.randomUUID());

        assertThat(receipt.requestId()).isNotNull();
        verify(requests).saveAndFlush(argThat(saved -> saved.getRequesterCui() == null
                && saved.getPatient() == null));
        verifyNoInteractions(patients, users, appointments);
    }

    @Test
    void receptionMustVerifyIdentityBeforeCreatingAndLinkingNewPatientAtomically() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", null,
                "5555-0101", null, null, UUID.randomUUID(), "payload-hash");
        Appointment guest = Appointment.forPublicRequest(UUID.randomUUID(), "Maria Lopez", "5555-0101",
                dentist, NOW.plusSeconds(3600), NOW);
        request.confirm(guest, secretary, NOW);
        request.verifyRequesterIdentity(secretary, "IN_PERSON", NOW);
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        patient.setDpi("1234567890123");
        CreatePatientRequest input = new CreatePatientRequest("Maria Lopez", "1234567890123",
                LocalDate.of(1990, 1, 1), Gender.FEMALE, "5555-0101", null,
                null, null, null, null, null, null, null, null, null, null);
        PatientResponse response = new PatientResponse(patient.getId(), patient.getCode(), patient.getName(),
                patient.getDpi(), patient.getBirthDate(), patient.getGender(), patient.getPhone(), null,
                null, null, null, null, null, null, null, null, null, null, null, NOW, NOW);
        when(patientService.create(input)).thenReturn(response);
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(requests.saveAndFlush(request)).thenReturn(request);

        var linked = service.registerAndLinkPublicRequester(secretary.getId(), request.getId(), input);

        assertThat(linked.patient().id()).isEqualTo(patient.getId());
        assertThat(linked.source()).isEqualTo("PUBLIC");
        assertThat(linked.identityVerification().method()).isEqualTo("IN_PERSON");
        assertThat(guest.getPatient()).isEqualTo(patient);
        verify(patientService).create(input);
        verifyNoInteractions(appointments);
    }

    @Test
    void receptionCannotLinkExistingPatientUntilIdentityIsVerified() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "payload-hash");
        request.confirm(Appointment.forPublicRequest(UUID.randomUUID(), "Maria Lopez", "5555-0101",
                dentist, NOW.plusSeconds(3600), NOW), secretary, NOW);
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.linkPublicRequestPatient(secretary.getId(), request.getId(), patient.getId()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "REQUESTER_IDENTITY_NOT_VERIFIED");
        verifyNoInteractions(patientService);
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
        when(requests.existsByIdempotencyPayloadHashAndStatusIn(anyString(), eq(List.of(
                AppointmentRequestStatus.PENDING_CLINIC, AppointmentRequestStatus.PENDING_PATIENT)))).thenReturn(true);
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
        CreatePublicAppointmentRequest request = new CreatePublicAppointmentRequest("Maria Lopez", "1234567890123",
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
    void legacyAcceptanceCannotConfirmPublicPendingRequest() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, dentist, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Juan Publico", null,
                "+502 5555-1234", null, "Limpieza", UUID.randomUUID(), "payload-hash");
        when(users.findById(secretary.getId())).thenReturn(Optional.of(secretary));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.acceptRequestedTime(secretary.getId(), request.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("telephone confirmation");
        verifyNoInteractions(appointments, messages, patientService);
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
        request.verifyRequesterIdentity(secretary, "DOCUMENT_REVIEW", NOW);
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
    void acceptingWithoutReceptionLinkedPatientReservesGuestAppointment() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", null,
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.assignProfessional(dentist, NOW);
        request.propose(dentist, FUTURE, NOW.plusSeconds(3600), secretary, NOW);
        String rawToken = "scoped-conversation-token";
        when(conversations.findByConversationTokenHash(anyString()))
                .thenReturn(Optional.of(verifiedConversation(request.getId(), rawToken)));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        Appointment guest = Appointment.forPublicRequest(UUID.randomUUID(), "Maria Lopez", "5555-0101",
                dentist, FUTURE, NOW);
        when(appointments.createPublic("Maria Lopez", "5555-0101", dentist.getId(), FUTURE))
                .thenReturn(guest);

        var accepted = service.decidePublicProposal(request.getId(), rawToken,
                new com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest(
                        com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest.Decision.ACCEPT),
                UUID.randomUUID());

        assertThat(accepted.status()).isEqualTo(AppointmentRequestStatus.CONFIRMED);
        assertThat(accepted.confirmedAt()).isEqualTo(FUTURE);
        assertThat(request.getPatient()).isNull();
        verify(appointments).createPublic("Maria Lopez", "5555-0101", dentist.getId(), FUTURE);
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
    void priorDecisionIdempotencyKeyCannotBeReplayedAgainstANewerProposal() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", patient.getDpi(),
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        request.propose(dentist, FUTURE, NOW.plusSeconds(3600), secretary, NOW);
        String rawToken = "scoped-conversation-token";
        UUID key = UUID.randomUUID();
        AppointmentPublicConversation conversation = verifiedConversation(request.getId(), rawToken);
        when(conversations.findByConversationTokenHash(anyString())).thenReturn(Optional.of(conversation));
        when(requests.findDetailedByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);
        when(conversationMessages.latest(request.getId(), 20)).thenReturn(List.of());

        service.decidePublicProposal(request.getId(), rawToken,
                new com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest(
                        com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest.Decision.REJECT), key);
        when(decisions.findByAppointmentRequestIdAndIdempotencyKey(request.getId(), key))
                .thenReturn(Optional.of(new AppointmentPublicDecision(request.getId(), key, "REJECT", NOW)));

        request.propose(dentist, FUTURE.plusSeconds(3600), NOW.plusSeconds(7200), secretary, NOW.plusSeconds(1));
        assertThatThrownBy(() -> service.decidePublicProposal(request.getId(), rawToken,
                new com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest(
                        com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest.Decision.ACCEPT), key))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_REUSED");
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
        request.verifyRequesterIdentity(secretary, "DOCUMENT_REVIEW", NOW);
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

    @Test
    void publicConversationRejectsClinicalContentAndDerivesPatientSenderFromScopedToken() {
        UUID requestId = UUID.randomUUID();
        String token = "token-for-this-request";
        when(conversationMessages.addPublic(eq(requestId), eq(token), any(), any()))
                .thenThrow(new BadRequestException("Scheduling messages only"));

        assertThatThrownBy(() -> service.addPublicMessage(requestId, token, UUID.randomUUID(),
                new com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentConversationMessageRequest(
                        "Me duele una muela")))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(messages);
    }

    @Test
    void publicMessageRetryWithSameIdempotencyKeyReturnsSameMessage() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, null, FUTURE,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Maria Lopez", null,
                "5555-0101", null, null, UUID.randomUUID(), "hash");
        String token = "token-for-this-request";
        UUID key = UUID.randomUUID();
        AppointmentRequestMessage message = new AppointmentRequestMessage(UUID.randomUUID(), request.getId(),
                "PATIENT", "FREE_TEXT", "Please call after 3 pm", NOW, key,
                tokenHash("Please call after 3 pm"));
        when(conversationMessages.addPublic(eq(request.getId()), eq(token), eq(key), any()))
                .thenReturn(new com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestMessageResponse(
                        message.getId(), message.getSender(), message.getMessageType(), message.getText(), NOW));

        var result = service.addPublicMessage(request.getId(), token, key,
                new com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentConversationMessageRequest(
                        "Please call after 3 pm"));

        assertThat(result.id()).isEqualTo(message.getId());
        assertThat(result.sender()).isEqualTo("PATIENT");
        verify(conversationMessages).addPublic(eq(request.getId()), eq(token), eq(key), any());
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
        value.setDpi("1234567890123");
        value.setBirthDate(LocalDate.of(1990, 1, 1));
        value.setGender(Gender.FEMALE);
        return value;
    }

    private User user(String roleCode) {
        User value = new User(UUID.randomUUID(), roleCode.toLowerCase() + UUID.randomUUID(), roleCode,
                roleCode.toLowerCase() + "@example.test", "1234567890123", "hash", UserStatus.ACTIVE, NOW, NOW);
        value.setRoles(Set.of(new Role(UUID.randomUUID(), roleCode, roleCode, null, true)));
        return value;
    }
}
