package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.dto.request.CreatePublicAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentRequestReceipt;
import com.dentalcare.api.modules.appointments.mapper.AppointmentRequestMapper;
import com.dentalcare.api.modules.appointments.model.*;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentPublicConversationRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentPublicConversationTokenRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestMessageRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentPublicDecisionRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.patients.service.PatientService;
import com.dentalcare.api.modules.patients.dto.request.CreatePatientRequest;
import com.dentalcare.api.modules.appointments.dto.request.VerifyPublicRequesterIdentityRequest;
import com.dentalcare.api.modules.users.model.*;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest;
import com.dentalcare.api.modules.appointments.dto.request.PublicVerificationChannelRequest;
import com.dentalcare.api.modules.appointments.dto.request.VerifyPublicAppointmentCodeRequest;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentConversationResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicConversationTokenResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicVerificationAcknowledgement;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestMessageResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentProfessionalResponse;
import com.dentalcare.api.modules.appointments.dto.request.ClinicSchedulingMessageRequest;
import com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentConversationMessageRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentConversationMessagesPageResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentNotificationOutboxResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentWhatsAppDraftResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AppointmentRequestServiceImpl implements AppointmentRequestService {
    private final AuditService auditService;
    private final PatientService patientService;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final List<AppointmentRequestStatus> ACTIVE_PUBLIC_STATUSES =
            List.of(AppointmentRequestStatus.PENDING_CLINIC, AppointmentRequestStatus.PENDING_PATIENT);
    private static final String REQUEST_NOT_PUBLIC = "APPOINTMENT_REQUEST_NOT_PUBLIC";
    private static final String REQUEST_STATE_NOT_ELIGIBLE = "APPOINTMENT_REQUEST_STATE_NOT_ELIGIBLE";
    private static final String PROFESSIONAL_NOT_AVAILABLE = "PROFESSIONAL_NOT_AVAILABLE";
    private static final String APPOINTMENT_TIME_UNAVAILABLE = "APPOINTMENT_TIME_UNAVAILABLE";

    private final AppointmentRequestRepository requests;
    private final PatientRepository patients;
    private final UserRepository users;
    private final AppointmentService appointments;
    private final AppointmentRequestMapper mapper;
    private final Clock clock;
    private final AppointmentPublicConversationRepository conversations;
    private final AppointmentPublicConversationTokenRepository retainedConversationTokens;
    private final AppointmentPublicDecisionRepository decisions;
    private final AppointmentRequestMessageRepository messages;
    private final AppointmentNotificationOutboxService notificationOutbox;
    private final AppointmentConversationMessageService conversationMessages;
    private final PublicAppointmentCodeDelivery codeDelivery;
    private final PasswordEncoder passwordEncoder;
    private final com.dentalcare.api.modules.appointments.repository.AppointmentRepository appointmentRepository;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private static final java.time.Duration OTP_TTL = java.time.Duration.ofMinutes(10);
    private static final java.time.Duration CONVERSATION_TTL = java.time.Duration.ofHours(24);
    private static final java.time.Duration PUBLIC_REQUEST_CONVERSATION_TTL = java.time.Duration.ofDays(30);
    private static final java.time.Duration CONVERSATION_RENEWAL_THRESHOLD = java.time.Duration.ofDays(7);
    private static final java.time.Duration PROPOSAL_TTL = java.time.Duration.ofHours(24);
    private static final int OTP_MAX_ATTEMPTS = 5;

    public AppointmentRequestServiceImpl(AppointmentRequestRepository requests, PatientRepository patients,
                                         UserRepository users, AppointmentService appointments,
                                         AppointmentRequestMapper mapper, Clock clock,
                                         PatientService patientService, AuditService auditService,
                                         AppointmentPublicConversationRepository conversations,
                                         AppointmentPublicConversationTokenRepository retainedConversationTokens,
                                         AppointmentPublicDecisionRepository decisions,
                                         AppointmentRequestMessageRepository messages,
                                         AppointmentNotificationOutboxService notificationOutbox,
                                         AppointmentConversationMessageService conversationMessages,
                                         PublicAppointmentCodeDelivery codeDelivery,
                                         PasswordEncoder passwordEncoder,
                                         com.dentalcare.api.modules.appointments.repository.AppointmentRepository appointmentRepository) {
        this.requests = requests;
        this.patients = patients;
        this.users = users;
        this.appointments = appointments;
        this.mapper = mapper;
        this.clock = clock;
        this.patientService = patientService;
        this.auditService = auditService;
        this.conversations = conversations;
        this.retainedConversationTokens = retainedConversationTokens;
        this.decisions = decisions;
        this.messages = messages;
        this.notificationOutbox = notificationOutbox;
        this.conversationMessages = conversationMessages;
        this.codeDelivery = codeDelivery;
        this.passwordEncoder = passwordEncoder;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    @Transactional
    public PublicAppointmentRequestReceipt createPublic(CreatePublicAppointmentRequest request, UUID idempotencyKey) {
        if (idempotencyKey == null) throw new BadRequestException("Idempotency-Key header is required");
        requireFuture(request.requestedAt(), "Requested date and time");

        String fullName = request.fullName().trim().replaceAll("\\s+", " ");
        String cui = request.cui() == null ? null : request.cui().trim();
        if (cui != null && !cui.isEmpty() && !cui.matches("^[0-9]{13}$")) {
            throw new BadRequestException("CUI must contain exactly 13 digits");
        }
        if (cui != null && cui.isEmpty()) cui = null;
        String phone = request.phone().trim();
        long phoneDigits = phone.chars().filter(Character::isDigit).count();
        if (phoneDigits < 7 || phoneDigits > 15) {
            throw new BadRequestException("Phone number must contain between 7 and 15 digits");
        }
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        String reason = request.reason() == null || request.reason().isBlank()
                ? null : request.reason().trim();
        NonClinicalSchedulingText.validate(reason);
        String payloadHash = publicPayloadHash(fullName, cui, phone, email, request.requestedAt(),
                request.professionalId(), reason);

        var repeated = requests.findByIdempotencyKey(idempotencyKey);
        if (repeated.isPresent()) {
            if (!payloadHash.equals(repeated.get().getIdempotencyPayloadHash())) {
                throw new ConflictException("Idempotency key was already used with different request data");
            }
            return issuePublicRequestReceipt(repeated.get().getId());
        }

        User professional = request.professionalId() == null ? null : publicDentist(request.professionalId());
        if (hasActiveEquivalent(payloadHash)) {
            throw new ConflictException("An equivalent appointment request is already active");
        }

        Instant now = clock.instant();
        AppointmentRequest created = new AppointmentRequest(UUID.randomUUID(), null, professional,
                request.requestedAt(), AppointmentRequestStatus.PENDING_CLINIC, now, now,
                fullName, cui, phone, email, reason, idempotencyKey, payloadHash);
        AppointmentRequest saved = requests.saveAndFlush(created);
        messages.save(new AppointmentRequestMessage(UUID.randomUUID(), saved.getId(), "BOT", "SCHEDULING_UPDATE",
                "Recibimos tu solicitud. El horario solicitado es una preferencia y aún no está confirmado.", now));
        auditService.success(AuditActions.APPOINTMENT_REQUEST_CREATED, "APPOINTMENTS",
                "PublicAppointmentRequest", saved.getId(), null);
        return issuePublicRequestReceipt(saved.getId());
    }

    @Override
    @Transactional
    public AppointmentRequestResponse linkPublicRequestPatient(UUID actorId, UUID requestId, UUID patientId) {
        actor(actorId);
        AppointmentRequest request = locked(requestId);
        requirePublicAppointmentDay(request);
        requireIdentityVerified(request);
        if (request.getPatient() != null) {
            if (request.getPatient().getId().equals(patientId)) {
                return administrativeResponse(request, historyFor(request.getId()));
            }
            throw new ConflictException("Appointment request is already linked to a patient");
        }
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        request.linkPatient(patient, clock.instant());
        request.getAppointment().linkPatient(patient, clock.instant());
        appointmentRepository.saveAndFlush(request.getAppointment());
        auditService.success(AuditActions.APPOINTMENT_REQUEST_PATIENT_LINKED, "APPOINTMENTS",
                "AppointmentRequest", request.getId(), actorId);
        AppointmentRequest saved = requests.saveAndFlush(request);
        return administrativeResponse(saved, historyFor(saved.getId()));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse verifyPublicRequesterIdentity(UUID actorId, UUID requestId,
            VerifyPublicRequesterIdentityRequest input) {
        User verifyingActor = actor(actorId);
        AppointmentRequest request = locked(requestId);
        requirePublicAppointmentDay(request);
        if (input.method() != VerifyPublicRequesterIdentityRequest.VerificationMethod.IN_PERSON) {
            throw new ConflictException("IN_PERSON_DPI_VERIFICATION_REQUIRED",
                    "Verify the physical DPI in person on the appointment day");
        }
        if (request.getRequesterIdentityVerifiedAt() == null) {
            request.verifyRequesterIdentity(verifyingActor, input.method().name(), clock.instant());
            auditService.success(AuditActions.APPOINTMENT_REQUEST_IDENTITY_VERIFIED, "APPOINTMENTS",
                    "AppointmentRequest", request.getId(), actorId);
        }
        AppointmentRequest saved = requests.saveAndFlush(request);
        return administrativeResponse(saved, historyFor(saved.getId()));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse registerAndLinkPublicRequester(UUID actorId, UUID requestId,
            CreatePatientRequest input) {
        User registeringActor = actor(actorId);
        AppointmentRequest request = locked(requestId);
        requirePublicAppointmentDay(request);
        requireIdentityVerified(request);

        String dpi = normalizeDpi(input.dpi());
        if (request.getPatient() != null) {
            if (request.getPatient().getDpi().equals(dpi)) {
                return administrativeResponse(request, historyFor(requestId));
            }
            throw new ConflictException("APPOINTMENT_REQUEST_ALREADY_LINKED",
                    "This appointment request is already linked to another patient record");
        }

        var patientResponse = patientService.create(input);
        Patient createdPatient = patients.findById(patientResponse.id())
                .orElseThrow(() -> new IllegalStateException("Created patient could not be reloaded"));
        request.linkPatient(createdPatient, clock.instant());
        request.getAppointment().linkPatient(createdPatient, clock.instant());
        appointmentRepository.saveAndFlush(request.getAppointment());
        auditService.success(AuditActions.PATIENT_CREATED_FROM_PUBLIC_APPOINTMENT_REQUEST, "PATIENTS",
                "Patient", createdPatient.getId(), actorId);
        auditService.success(AuditActions.APPOINTMENT_REQUEST_PATIENT_LINKED, "APPOINTMENTS",
                "AppointmentRequest", request.getId(), actorId);
        AppointmentRequest saved = requests.saveAndFlush(request);
        return administrativeResponse(saved, historyFor(saved.getId()));
    }

    private void requireIdentityVerified(AppointmentRequest request) {
        if (request.getRequesterIdentityVerifiedAt() == null
                || !"IN_PERSON".equals(request.getRequesterIdentityVerificationMethod())) {
            throw new ConflictException("REQUESTER_IDENTITY_NOT_VERIFIED",
                    "Reception must verify the physical DPI in person before linking a patient record");
        }
    }

    private void requirePublicAppointmentDay(AppointmentRequest request) {
        if (request.getRequesterFullName() == null || request.getStatus() != AppointmentRequestStatus.CONFIRMED
                || request.getAppointment() == null
                || request.getAppointment().getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ConflictException("APPOINTMENT_REQUEST_STATE_NOT_ELIGIBLE",
                    "Patient registration and linkage require a confirmed public appointment");
        }
        LocalDate appointmentDate = request.getAppointment().getScheduledAt()
                .atZone(ZoneId.of("America/Guatemala")).toLocalDate();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Guatemala")));
        if (!appointmentDate.equals(today)) {
            throw new ConflictException("IN_PERSON_DPI_VERIFICATION_REQUIRED",
                    "Verify the physical DPI on the scheduled clinic day");
        }
    }

    private String normalizeDpi(String value) {
        if (value == null || !value.trim().matches("[0-9 ]+")) {
            throw new BadRequestException("DPI must contain exactly 13 digits");
        }
        String normalized = value.replace(" ", "").trim();
        if (!normalized.matches("[0-9]{13}")) {
            throw new BadRequestException("DPI must contain exactly 13 digits");
        }
        return normalized;
    }

    @Override
    @Transactional
    public AppointmentRequestResponse assignPublicRequestProfessional(UUID actorId, UUID requestId, UUID professionalId) {
        actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (request.getRequesterFullName() == null) {
            throw new ConflictException(REQUEST_NOT_PUBLIC,
                    "Cannot assign a dentist: this is not a public appointment request");
        }
        if (!isOpen(request)) {
            throw new ConflictException(REQUEST_STATE_NOT_ELIGIBLE,
                    "Cannot assign a dentist while request status is " + request.getStatus()
                            + "; eligible statuses are PENDING and PROPOSED");
        }
        User professional;
        try {
            professional = dentist(professionalId);
        } catch (ResourceNotFoundException | ConflictException exception) {
            throw new ConflictException(PROFESSIONAL_NOT_AVAILABLE,
                    "Cannot assign dentist: selected professional must exist and be an active dentist");
        }
        request.assignProfessional(professional, clock.instant());
        auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED, "APPOINTMENTS",
                "AppointmentRequest", request.getId(), actorId);
        AppointmentRequest saved = requests.saveAndFlush(request);
        return administrativeResponse(saved, historyFor(saved.getId()));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse confirmPublicProposal(UUID actorId, UUID requestId) {
        User actor = actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (request.getRequesterFullName() == null) {
            throw new ConflictException("This confirmation action is reserved for public requests");
        }
        if (request.getPatient() == null) {
            throw new ConflictException("APPOINTMENT_REQUEST_PATIENT_LINK_REQUIRED",
                    "Cannot confirm public proposal until the requester is linked to a patient record");
        }
        throw new ConflictException("PUBLIC_PATIENT_ACCEPTANCE_REQUIRED",
                "Public proposals must be accepted by the requester through the verified conversation");
    }

    private boolean hasActiveEquivalent(String payloadHash) {
        return requests.existsByIdempotencyPayloadHashAndStatusIn(payloadHash, ACTIVE_PUBLIC_STATUSES);
    }

    private User publicDentist(UUID professionalId) {
        try {
            return dentist(professionalId);
        } catch (ResourceNotFoundException exception) {
            throw new ConflictException("Selected professional is not available");
        }
    }

    private String publicPayloadHash(String fullName, String cui, String phone, String email,
                                     Instant requestedAt, UUID professionalId, String reason) {
        try {
            String canonical = String.join("\n", fullName, nullToEmpty(cui), phone, nullToEmpty(email),
                    requestedAt.toString(), nullToEmpty(professionalId == null ? null : professionalId.toString()),
                    nullToEmpty(reason));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    @Transactional
    public AppointmentRequestResponse createForPatient(UUID userId, UUID professionalId, Instant requestedAt) {
        Patient patient = patientFor(userId);
        User professional = dentist(professionalId);
        requireFuture(requestedAt, "Requested date and time");
        Instant now = clock.instant();
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, professional,
                requestedAt, AppointmentRequestStatus.PENDING, now, now);
        AppointmentRequest saved=requests.saveAndFlush(request);
        if(auditService!=null)auditService.success(AuditActions.APPOINTMENT_REQUEST_CREATED,"APPOINTMENTS","AppointmentRequest",saved.getId(),userId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentRequestResponse> findForPatient(UUID userId, int page, int size) {
        Patient patient = patientFor(userId);
        return requests.findByPatient_Id(patient.getId(), page(page, size)).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentRequestResponse findOwned(UUID userId, UUID requestId) {
        Patient patient = patientFor(userId);
        return requests.findByIdAndPatient_Id(requireId(requestId), patient.getId())
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse acceptProposal(UUID userId, UUID requestId) {
        Patient patient = patientFor(userId);
        AppointmentRequest request = lockedOwned(requestId, patient.getId());
        if (request.getStatus() == AppointmentRequestStatus.CONFIRMED) return mapper.toResponse(request);
        if (request.getStatus() != AppointmentRequestStatus.PROPOSED) {
            throw new ConflictException("Only a proposed appointment request can be accepted by the patient");
        }
        Appointment appointment = appointments.create(patient.getId(), request.getProposedProfessional().getId(),
                request.getProposedAt());
        request.confirm(appointment, request.getProcessedBy(), clock.instant());
        return mapper.toResponse(requests.saveAndFlush(request));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse rejectProposal(UUID userId, UUID requestId) {
        Patient patient = patientFor(userId);
        AppointmentRequest request = lockedOwned(requestId, patient.getId());
        if (request.getStatus() != AppointmentRequestStatus.PROPOSED) {
            throw new ConflictException("Only a proposed appointment request can be rejected by the patient");
        }
        request.reject(request.getProcessedBy(), clock.instant());
        return mapper.toResponse(requests.saveAndFlush(request));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse cancelForPatient(UUID userId, UUID requestId) {
        Patient patient = patientFor(userId);
        AppointmentRequest request = lockedOwned(requestId, patient.getId());
        if (request.getStatus() != AppointmentRequestStatus.PENDING) {
            throw new ConflictException("Only a pending appointment request can be cancelled");
        }
        request.cancel(clock.instant());
        return mapper.toResponse(requests.saveAndFlush(request));
    }

    @Override
    @Transactional
    public Page<AppointmentRequestResponse> findAll(Instant from, Instant to, UUID patientId,
                                                     UUID professionalId, AppointmentRequestStatus status,
                                                     int page, int size) {
        expireStaleProposals();
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("From date must not be after to date");
        }
        Specification<AppointmentRequest> spec = Specification.unrestricted();
        if (from != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("requestedAt"), from));
        if (to != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("requestedAt"), to));
        if (patientId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("patient").get("id"), patientId));
        if (professionalId != null) spec = spec.and((root, query, cb) -> {
            var requested = root.join("requestedProfessional", jakarta.persistence.criteria.JoinType.LEFT);
            var assigned = root.join("assignedProfessional", jakarta.persistence.criteria.JoinType.LEFT);
            var proposed = root.join("proposedProfessional", jakarta.persistence.criteria.JoinType.LEFT);
            return cb.or(cb.equal(requested.get("id"), professionalId),
                    cb.equal(assigned.get("id"), professionalId),
                    cb.equal(proposed.get("id"), professionalId));
        });
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        Page<AppointmentRequest> result = requests.findAll(spec, page(page, size));
        List<AppointmentRequest> content = result.getContent();
        if (content.isEmpty()) return result.map(mapper::toAdministrativeResponse);
        List<AppointmentRequestResponse> responses = content.stream()
                .map(value -> administrativeResponse(value, List.of())).toList();
        return new PageImpl<AppointmentRequestResponse>(responses, result.getPageable(), result.getTotalElements());
    }

    @Override
    @Transactional
    public AppointmentRequestResponse findById(UUID requestId) {
        AppointmentRequest request = requests.findDetailedById(requireId(requestId))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
        if (request.getStatus() == AppointmentRequestStatus.PENDING_PATIENT && request.getProposedExpiresAt() != null
                && !request.getProposedExpiresAt().isAfter(clock.instant())) {
            AppointmentRequest lockedRequest = locked(request.getId());
            expireProposal(lockedRequest, clock.instant());
            request = lockedRequest;
        }
        AppointmentRequest selected = request;
        return java.util.Optional.of(selected).map(value -> administrativeResponse(value, historyFor(value.getId())))
                .orElseThrow();
    }

    @Override
    @Transactional
    public AppointmentRequestResponse acceptRequestedTime(UUID actorId, UUID requestId) {
        User actor = actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (request.getStatus() == AppointmentRequestStatus.CONFIRMED) {
            return administrativeResponse(request, historyFor(request.getId()));
        }
        boolean isPublic = request.getRequesterFullName() != null;
        if (isPublic) {
            throw new ConflictException(REQUEST_STATE_NOT_ELIGIBLE,
                    "Public first appointments require telephone confirmation");
        } else {
            if (request.getStatus() != AppointmentRequestStatus.PENDING) {
                throw new ConflictException(REQUEST_STATE_NOT_ELIGIBLE,
                        "Cannot accept requested time while request status is " + request.getStatus()
                                + "; required status is PENDING");
            }
            if (request.getPatient() == null) {
                throw new ConflictException("APPOINTMENT_REQUEST_PATIENT_LINK_REQUIRED",
                        "Cannot confirm requested time until the requester is linked to a patient record");
            }
        }
        User selectedProfessional = isPublic && request.getAssignedProfessional() != null
                ? request.getAssignedProfessional() : request.getRequestedProfessional();
        if (selectedProfessional == null) {
            throw new ConflictException("APPOINTMENT_REQUEST_PROFESSIONAL_ASSIGNMENT_REQUIRED",
                    "Cannot accept requested time until a dentist is assigned or selected in the request");
        }
        Appointment appointment = createAppointment(request, selectedProfessional, request.getRequestedAt());
        request.confirm(appointment, actor, clock.instant());
        if (isPublic) {
            messages.save(new AppointmentRequestMessage(UUID.randomUUID(), request.getId(), "RECEPTION", "SCHEDULING_UPDATE",
                    "Tu cita fue confirmada para el " + request.getRequestedAt() + " con el Dr(a). " + selectedProfessional.getFullName() + ". Te esperamos en la clínica; por favor presenta tu DPI físico al llegar.", clock.instant()));
            enqueueNotice(request, "Tu cita fue confirmada. Ingresa al portal para consultar los detalles.");
        }
        if (auditService != null) {
            auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED, "APPOINTMENTS",
                    "AppointmentRequest", request.getId(), actorId);
        }
        AppointmentRequest saved = requests.saveAndFlush(request);
        return administrativeResponse(saved, historyFor(saved.getId()));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse propose(UUID actorId, UUID requestId, UUID professionalId, Instant proposedAt) {
        User actor = actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (!isOpen(request)) {
            throw new ConflictException(REQUEST_STATE_NOT_ELIGIBLE,
                    "Cannot propose a time while request status is " + request.getStatus()
                            + "; eligible statuses are PENDING and PROPOSED");
        }
        requireFuture(proposedAt, "Proposed date and time");
        User professional = professionalId == null
                ? (request.getRequesterFullName() != null && request.getAssignedProfessional() != null
                    ? request.getAssignedProfessional() : request.getRequestedProfessional())
                : dentist(professionalId);
        if (professional == null) {
            throw new ConflictException("APPOINTMENT_REQUEST_PROFESSIONAL_ASSIGNMENT_REQUIRED",
                    "Select or assign an active dentist before proposing an appointment time");
        }
        if (appointmentRepository.existsByProfessional_IdAndScheduledAtAndStatus(
                professional.getId(), proposedAt, AppointmentStatus.SCHEDULED)) {
            throw new ConflictException("APPOINTMENT_TIME_UNAVAILABLE", "The selected appointment time is already occupied");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(PROPOSAL_TTL);
        request.propose(professional, proposedAt, expiresAt, actor, now);
        messages.save(new AppointmentRequestMessage(UUID.randomUUID(), request.getId(), "RECEPTION", "PROPOSAL",
                "Recepción propone " + proposedAt + " con " + professional.getFullName() + ". Puedes aceptar o rechazar esta propuesta.", now));
        enqueueNotice(request, "Recepción propuso un horario. Ingresa al portal para revisar y responder la propuesta.");
        if(auditService!=null)auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED,"APPOINTMENTS","AppointmentRequest",request.getId(),actorId);
        AppointmentRequest saved = requests.saveAndFlush(request);
        return administrativeResponse(saved, historyFor(saved.getId()));
    }

    @Override
    @Transactional
    public PublicVerificationAcknowledgement requestConversationCode(UUID requestId,
            PublicVerificationChannelRequest request) {
        String generic = "If the request and selected channel are valid, a verification code will be sent.";
        if (!codeDelivery.isConfigured(request.channel())) {
            throw new ServiceUnavailableException("Public appointment verification is temporarily unavailable");
        }
        AppointmentRequest appointmentRequest = requests.findById(requestId).orElse(null);
        if (appointmentRequest == null || appointmentRequest.getRequesterFullName() == null) {
            return new PublicVerificationAcknowledgement(generic);
        }
        String destination = request.channel() == PublicVerificationChannelRequest.Channel.EMAIL
                ? appointmentRequest.getRequesterEmail() : appointmentRequest.getRequesterPhone();
        if (destination == null || destination.isBlank()) return new PublicVerificationAcknowledgement(generic);
        Instant now = clock.instant();
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        AppointmentPublicConversation conversation = conversations.findForUpdate(requestId).orElse(null);
        if (conversation == null) {
            conversation = new AppointmentPublicConversation(requestId, request.channel().name(),
                    passwordEncoder.encode(code), now.plus(OTP_TTL), now);
        } else {
            conversation.replaceCode(request.channel().name(), passwordEncoder.encode(code), now.plus(OTP_TTL), now);
        }
        conversations.saveAndFlush(conversation);
        notificationOutbox.enqueueOtp(requestId, request.channel(), destination, code, OTP_TTL);
        return new PublicVerificationAcknowledgement(generic);
    }

    @Override
    @Transactional(noRollbackFor = {UnauthorizedException.class, GoneException.class,
            com.dentalcare.api.security.ratelimit.RateLimitExceededException.class})
    public PublicConversationTokenResponse verifyConversationCode(UUID requestId,
            VerifyPublicAppointmentCodeRequest input) {
        AppointmentPublicConversation conversation = conversations.findForUpdate(requestId)
                .orElseThrow(() -> new UnauthorizedException("Invalid verification code"));
        Instant now = clock.instant();
        if (conversation.getVerificationExpiresAt() == null) {
            throw new GoneException("Verification code was already used or has expired");
        }
        if (!conversation.getVerificationExpiresAt().isAfter(now)) {
            throw new GoneException("Verification code has expired");
        }
        if (conversation.getVerificationAttempts() >= OTP_MAX_ATTEMPTS) {
            throw new com.dentalcare.api.security.ratelimit.RateLimitExceededException(600);
        }
        if (!passwordEncoder.matches(input.code(), conversation.getVerificationCodeHash())) {
            conversation.registerFailedAttempt(now);
            conversations.saveAndFlush(conversation);
            if (conversation.getVerificationAttempts() >= OTP_MAX_ATTEMPTS) {
                throw new com.dentalcare.api.security.ratelimit.RateLimitExceededException(600);
            }
            throw new UnauthorizedException("Invalid verification code");
        }
        String token = randomToken();
        if (conversation.getConversationTokenHash() != null
                && conversation.getConversationExpiresAt() != null
                && conversation.getConversationExpiresAt().isAfter(now)) {
            retainedConversationTokens.save(new AppointmentPublicConversationToken(
                    conversation.getConversationTokenHash(), requestId, conversation.getConversationExpiresAt()));
        }
        conversation.consumeCode(sha256(token), now.plus(CONVERSATION_TTL), now);
        conversations.saveAndFlush(conversation);
        return new PublicConversationTokenResponse(token, "Bearer", now.plus(CONVERSATION_TTL));
    }

    @Override
    @Transactional
    public PublicAppointmentConversationResponse getPublicConversation(UUID requestId, String token) {
        AppointmentPublicConversation conversation = authorizedConversation(requestId, token);
        Instant now = clock.instant();
        boolean currentToken = sha256(token).equals(conversation.getConversationTokenHash());
        if (currentToken && conversation.getConversationExpiresAt().isBefore(now.plus(CONVERSATION_RENEWAL_THRESHOLD))) {
            conversation.extendTokenExpiry(now.plus(PUBLIC_REQUEST_CONVERSATION_TTL), now);
            conversations.save(conversation);
        }
        AppointmentRequest request = requests.findDetailedById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
        if (request.getStatus() == AppointmentRequestStatus.PENDING_PATIENT
                && request.getProposedExpiresAt() != null && !request.getProposedExpiresAt().isAfter(now)) {
            request = locked(requestId);
            expireProposalIfNeeded(request, conversation);
        }
        return conversationResponse(request, tokenExpiry(requestId, token, conversation));
    }

    @Override
    @Transactional(noRollbackFor = {GoneException.class})
    public PublicAppointmentConversationResponse decidePublicProposal(UUID requestId, String token,
            PublicAppointmentDecisionRequest input, UUID idempotencyKey) {
        if (idempotencyKey == null) throw new BadRequestException("Idempotency-Key header is required");
        AppointmentPublicConversation conversation = authorizedConversation(requestId, token);
        AppointmentRequest request = locked(requestId);
        var previousDecision = decisions.findByAppointmentRequestIdAndIdempotencyKey(requestId, idempotencyKey);
        if (previousDecision.isPresent()) {
            if (!input.decision().name().equals(previousDecision.get().getDecision())) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was used for another decision");
            }
            return conversationResponse(request, tokenExpiry(requestId, token, conversation));
        }
        if (request.getStatus() != AppointmentRequestStatus.PENDING_PATIENT) {
            throw new ConflictException("PROPOSAL_ALREADY_RESPONDED", "There is no unanswered proposal for this request");
        }
        Instant now = clock.instant();
        if (request.getProposedExpiresAt() == null || !request.getProposedExpiresAt().isAfter(now)) {
            expireProposal(request, now);
            throw new GoneException("Appointment proposal has expired; reception can send another option");
        }
        if (input.decision() == PublicAppointmentDecisionRequest.Decision.REJECT) {
            request.returnToClinic(now);
            try {
                decisions.save(new AppointmentPublicDecision(requestId, idempotencyKey, "REJECT", now));
                messages.save(new AppointmentRequestMessage(UUID.randomUUID(), requestId, "PATIENT", "DECISION",
                        "La persona rechazó la propuesta. Recepción puede enviar otra alternativa.", now));
                requests.saveAndFlush(request);
                conversations.saveAndFlush(conversation);
            } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                throw new ConflictException("PROPOSAL_ALREADY_RESPONDED",
                        "The appointment proposal has already been decided or updated; refreshing status");
            }
            return conversationResponse(request, tokenExpiry(requestId, token, conversation));
        }
        Appointment appointment;
        try {
            appointment = createAppointment(request, request.getProposedProfessional(), request.getProposedAt());
        } catch (ConflictException exception) {
            if (APPOINTMENT_TIME_UNAVAILABLE.equals(exception.getCode())
                    || "Appointment time is not available".equals(exception.getMessage())) {
                throw new ConflictException(APPOINTMENT_TIME_UNAVAILABLE,
                        "The proposed appointment time is no longer available; reception must propose another time");
            }
            throw exception;
        }
        request.confirm(appointment, request.getProcessedBy(), now);
        try {
            decisions.save(new AppointmentPublicDecision(requestId, idempotencyKey, "ACCEPT", now));
            messages.save(new AppointmentRequestMessage(UUID.randomUUID(), requestId, "SYSTEM", "DECISION",
                    "La propuesta fue aceptada y la cita quedó confirmada.", now));
            requests.saveAndFlush(request);
            conversations.saveAndFlush(conversation);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new ConflictException("PROPOSAL_ALREADY_RESPONDED",
                    "The appointment proposal has already been decided or updated; refreshing status");
        }
        return conversationResponse(request, tokenExpiry(requestId, token, conversation));
    }

    @Override
    @Transactional(readOnly = true)
    public com.dentalcare.api.modules.appointments.dto.response.AppointmentAvailabilityResponse getAvailability(
            UUID professionalId, java.time.LocalDate date) {
        if (date == null) throw new BadRequestException("Availability date is required");
        User professional = dentist(professionalId);
        java.time.ZoneId zone = java.time.ZoneId.of("America/Guatemala");
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<Instant> booked = appointmentRepository
                .findByProfessional_IdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndStatusOrderByScheduledAtAsc(
                        professional.getId(), from, to, AppointmentStatus.SCHEDULED).stream()
                .map(Appointment::getScheduledAt).toList();
        return new com.dentalcare.api.modules.appointments.dto.response.AppointmentAvailabilityResponse(
                professional.getId(), date, zone.getId(), booked);
    }

    @Override
    @Transactional
    public AppointmentRequestResponse addSchedulingMessage(UUID actorId, UUID requestId,
            ClinicSchedulingMessageRequest input) {
        actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (request.getRequesterFullName() == null || !isOpen(request)) {
            throw new ConflictException("APPOINTMENT_REQUEST_STATE_NOT_ELIGIBLE",
                    "Scheduling messages are only available on open public requests");
        }
        String text = switch (input.type()) {
            case RECEPTION_FOLLOW_UP -> "Recepción está revisando tu solicitud y te enviará una alternativa de horario.";
            case REQUEST_RECEIVED -> "Recepción recibió tu solicitud. El horario preferido aún no está confirmado.";
        };
        Instant now = clock.instant();
        messages.save(new AppointmentRequestMessage(UUID.randomUUID(), requestId, "RECEPTION",
                "SCHEDULING_UPDATE", text, now));
        enqueueNotice(request, text);
        return administrativeResponse(requests.saveAndFlush(request), historyFor(requestId));
    }

    private AppointmentPublicConversation authorizedConversation(UUID requestId, String token) {
        if (token == null || token.isBlank()) throw new UnauthorizedException("Conversation token is required");
        String hash = sha256(token);
        Instant now = clock.instant();
        AppointmentPublicConversation conversation = conversations.findByConversationTokenHash(hash)
                .filter(value -> requestId.equals(value.getAppointmentRequestId())).orElse(null);
        if (conversation == null) {
            var retained = retainedConversationTokens.findById(hash)
                    .filter(value -> requestId.equals(value.getAppointmentRequestId()))
                    .orElseThrow(() -> new UnauthorizedException("Invalid conversation token"));
            if (!retained.getExpiresAt().isAfter(now)) {
                throw new GoneException("Conversation token has expired; verify your contact again");
            }
            if (retained.getExpiresAt().isBefore(now.plus(CONVERSATION_RENEWAL_THRESHOLD))) {
                retained.extendExpiry(now.plus(PUBLIC_REQUEST_CONVERSATION_TTL));
                retainedConversationTokens.save(retained);
            }
            return conversations.findById(requestId)
                    .orElseThrow(() -> new UnauthorizedException("Invalid conversation token"));
        }
        if (conversation.getConversationExpiresAt() == null || !conversation.getConversationExpiresAt().isAfter(now)) {
            throw new GoneException("Conversation token has expired; verify your contact again");
        }
        if (conversation.getConversationExpiresAt().isBefore(now.plus(CONVERSATION_RENEWAL_THRESHOLD))) {
            conversation.extendTokenExpiry(now.plus(PUBLIC_REQUEST_CONVERSATION_TTL), now);
            conversations.save(conversation);
        }
        return conversation;
    }

    private Instant tokenExpiry(UUID requestId, String token, AppointmentPublicConversation conversation) {
        String hash = sha256(token);
        if (hash.equals(conversation.getConversationTokenHash())) return conversation.getConversationExpiresAt();
        return retainedConversationTokens.findById(hash)
                .filter(value -> requestId.equals(value.getAppointmentRequestId()))
                .map(AppointmentPublicConversationToken::getExpiresAt)
                .orElseThrow(() -> new UnauthorizedException("Invalid conversation token"));
    }

    private void expireProposalIfNeeded(AppointmentRequest request, AppointmentPublicConversation conversation) {
        if (request.getStatus() == AppointmentRequestStatus.PENDING_PATIENT
                && request.getProposedExpiresAt() != null && !request.getProposedExpiresAt().isAfter(clock.instant())) {
            expireProposal(request, clock.instant());
        }
    }

    private void expireProposal(AppointmentRequest request, Instant now) {
        request.returnToClinic(now);
        messages.save(new AppointmentRequestMessage(UUID.randomUUID(), request.getId(), "SYSTEM", "SCHEDULING_UPDATE",
                "La propuesta venció. Recepción puede enviar otra alternativa.", now));
        requests.saveAndFlush(request);
    }

    private boolean isOpen(AppointmentRequest request) {
        if (request.getRequesterFullName() != null) {
            return request.getStatus() == AppointmentRequestStatus.PENDING_CLINIC
                    || request.getStatus() == AppointmentRequestStatus.PENDING_PATIENT
                    || request.getStatus() == AppointmentRequestStatus.PENDING;
        }
        return request.getStatus() == AppointmentRequestStatus.PENDING
                || request.getStatus() == AppointmentRequestStatus.PROPOSED;
    }

    private void expireStaleProposals() {
        Instant now = clock.instant();
        requests.findByStatusAndProposedExpiresAtLessThanEqual(AppointmentRequestStatus.PENDING_PATIENT, now)
                .forEach(value -> {
                    AppointmentRequest request = locked(value.getId());
                    if (request.getStatus() == AppointmentRequestStatus.PENDING_PATIENT
                            && request.getProposedExpiresAt() != null
                            && !request.getProposedExpiresAt().isAfter(now)) expireProposal(request, now);
                });
    }

    private PublicAppointmentConversationResponse conversationResponse(AppointmentRequest request,
            Instant tokenExpiresAt) {
        List<AppointmentRequestMessageResponse> history = historyFor(request.getId()).stream()
                .map(value -> new AppointmentRequestMessageResponse(value.getId(), value.getSender(),
                        value.getMessageType(), value.getText(), value.getCreatedAt())).toList();
        User professional = request.getStatus() == AppointmentRequestStatus.PENDING_PATIENT
                ? request.getProposedProfessional() : null;
        Appointment confirmed = request.getStatus() == AppointmentRequestStatus.CONFIRMED
                ? request.getAppointment() : null;
        return new PublicAppointmentConversationResponse(request.getId(), request.getStatus(),
                professional == null ? null : new AppointmentProfessionalResponse(professional.getId(), professional.getFullName()),
                request.getStatus() == AppointmentRequestStatus.PENDING_PATIENT ? request.getProposedAt() : null,
                request.getStatus() == AppointmentRequestStatus.PENDING_PATIENT ? request.getProposedExpiresAt() : null,
                request.getAppointment() == null ? null : request.getAppointment().getId(), history,
                confirmed == null ? null : confirmed.getScheduledAt(),
                confirmed == null ? null : new AppointmentProfessionalResponse(
                        confirmed.getProfessional().getId(), confirmed.getProfessional().getFullName()),
                tokenExpiresAt);
    }

    private AppointmentRequestResponse administrativeResponse(AppointmentRequest request,
                                                               List<AppointmentRequestMessage> history) {
        AppointmentRequestResponse value = mapper.toAdministrativeResponse(request);
        return new AppointmentRequestResponse(value.id(), value.patient(), value.requestedProfessional(),
                value.requestedAt(), value.proposedProfessional(), value.proposedAt(), value.status(),
                value.actionRequiredBy(), value.appointmentId(), value.createdAt(), value.updatedAt(),
                value.publicRequester(), value.source(), value.contact(), value.assignedProfessional(),
                value.proposalExpiresAt(), !"PUBLIC".equals(value.source()) ? null : history.stream().map(message -> new AppointmentRequestMessageResponse(
                        message.getId(), message.getSender(), message.getMessageType(), message.getText(),
                        message.getCreatedAt())).toList(), value.identityVerification());
    }

    private List<AppointmentRequestMessage> historyFor(UUID requestId) {
        return conversationMessages.latest(requestId, 20);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentConversationMessagesPageResponse getPublicMessages(UUID requestId, String token,
            String cursor, int size) {
        return conversationMessages.getPublic(requestId, token, cursor, size);
    }

    @Override
    @Transactional
    public AppointmentRequestMessageResponse addPublicMessage(UUID requestId, String token, UUID key,
            CreateAppointmentConversationMessageRequest input) {
        return conversationMessages.addPublic(requestId, token, key, input);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentConversationMessagesPageResponse getAdministrativeMessages(UUID requestId,
            String cursor, int size) {
        return conversationMessages.getAdministrative(requestId, cursor, size);
    }

    @Override
    @Transactional
    public AppointmentRequestMessageResponse addAdministrativeMessage(UUID actorId, UUID requestId, UUID key,
            CreateAppointmentConversationMessageRequest input) {
        return conversationMessages.addAdministrative(actorId, requestId, key, input);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentNotificationOutboxResponse> getNotificationStatus(UUID requestId, int page, int size) {
        AppointmentRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
        if (request.getRequesterFullName() == null) {
            throw new ConflictException(REQUEST_NOT_PUBLIC, "Notification history is available only for public requests");
        }
        return notificationOutbox.findForRequest(requestId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentWhatsAppDraftResponse createWhatsAppDraft(UUID requestId) {
        AppointmentRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
        if (request.getRequesterFullName() == null || request.getRequesterPhone() == null
                || request.getRequesterPhone().isBlank()) {
            throw new ConflictException(REQUEST_NOT_PUBLIC, "A public request with a phone number is required");
        }
        String rawPhone = request.getRequesterPhone().trim();
        String digits = rawPhone.replaceAll("\\D", "");
        String international = rawPhone.startsWith("+") || digits.startsWith("502") ? digits : "502" + digits;
        String text = "Hola " + request.getRequesterFullName()
                + ", te contactamos de DentalCare para coordinar tu solicitud de cita. Responde por este medio.";
        String url = "https://wa.me/" + international + "?text="
                + java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
        return new AppointmentWhatsAppDraftResponse(requestId, international, text, url, false);
    }

    private AppointmentRequestMessageResponse saveTextMessage(UUID requestId, String sender, UUID key, String rawText) {
        if (key == null) throw new BadRequestException("Idempotency-Key UUID is required");
        if (rawText == null || rawText.isBlank() || rawText.length() > 500
                || rawText.indexOf('<') >= 0 || rawText.indexOf('>') >= 0
                || rawText.chars().anyMatch(Character::isISOControl)) {
            throw new BadRequestException("Message must be plain text between 1 and 500 characters");
        }
        String text = rawText.trim();
        NonClinicalSchedulingText.validate(text);
        AppointmentRequest request = locked(requestId);
        if (request.getRequesterFullName() == null || !isOpen(request)) {
            throw new ConflictException(REQUEST_STATE_NOT_ELIGIBLE, "Messages require an open public request");
        }
        String hash = sha256(text);
        var previous = messages.findByAppointmentRequestIdAndSenderAndIdempotencyKey(requestId, sender, key);
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getIdempotencyPayloadHash())) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was used with different message content");
            }
            return messageResponse(previous.get());
        }
        AppointmentRequestMessage saved = messages.saveAndFlush(new AppointmentRequestMessage(UUID.randomUUID(),
                requestId, sender, "FREE_TEXT", text, clock.instant(), key, hash));
        if ("RECEPTION".equals(sender)) enqueueNotice(request, "Recepción te envió un mensaje. Ingresa al portal para consultarlo.");
        return messageResponse(saved);
    }

    private AppointmentConversationMessagesPageResponse messagePage(UUID requestId, String cursor, int size) {
        if (size < 1 || size > 100) throw new BadRequestException("size must be between 1 and 100");
        List<AppointmentRequestMessage> found;
        if (cursor == null || cursor.isBlank()) {
            found = messages.findLatest(requestId, PageRequest.of(0, size + 1));
        } else {
            String decoded;
            try {
                decoded = new String(java.util.Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Invalid message cursor");
            }
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) throw new BadRequestException("Invalid message cursor");
            try {
                found = messages.findOlderThan(requestId, Instant.parse(parts[0]), UUID.fromString(parts[1]),
                        PageRequest.of(0, size + 1));
            } catch (RuntimeException exception) {
                throw new BadRequestException("Invalid message cursor");
            }
        }
        boolean more = found.size() > size;
        if (more) found = new java.util.ArrayList<>(found.subList(0, size));
        String next = null;
        if (more && !found.isEmpty()) {
            AppointmentRequestMessage last = found.get(found.size() - 1);
            next = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (last.getCreatedAt() + "|" + last.getId()).getBytes(StandardCharsets.UTF_8));
        }
        java.util.Collections.reverse(found);
        return new AppointmentConversationMessagesPageResponse(found.stream().map(this::messageResponse).toList(), next, more, size);
    }

    private AppointmentRequestMessageResponse messageResponse(AppointmentRequestMessage message) {
        return new AppointmentRequestMessageResponse(message.getId(), message.getSender(), message.getMessageType(),
                message.getText(), message.getCreatedAt());
    }

    private void enqueueNotice(AppointmentRequest request, String text) {
        if (request.getRequesterEmail() != null && !request.getRequesterEmail().isBlank()
                && codeDelivery.isConfigured(PublicVerificationChannelRequest.Channel.EMAIL)) {
            notificationOutbox.enqueueNotice(request.getId(), PublicVerificationChannelRequest.Channel.EMAIL,
                    request.getRequesterEmail(), text);
        } else if (request.getRequesterPhone() != null && !request.getRequesterPhone().isBlank()
                && codeDelivery.isConfigured(PublicVerificationChannelRequest.Channel.SMS)) {
            notificationOutbox.enqueueNotice(request.getId(), PublicVerificationChannelRequest.Channel.SMS,
                    request.getRequesterPhone(), text);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private PublicAppointmentRequestReceipt issuePublicRequestReceipt(UUID requestId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(PUBLIC_REQUEST_CONVERSATION_TTL);
        String token = randomToken();
        AppointmentPublicConversation conversation = conversations.findForUpdate(requestId).orElse(null);
        if (conversation == null) {
            conversation = new AppointmentPublicConversation(requestId, "WEB", null, null, now);
        } else if (conversation.getConversationTokenHash() != null
                && conversation.getConversationExpiresAt() != null
                && conversation.getConversationExpiresAt().isAfter(now)) {
            retainedConversationTokens.save(new AppointmentPublicConversationToken(
                    conversation.getConversationTokenHash(), requestId, conversation.getConversationExpiresAt()));
        }
        conversation.issueToken(sha256(token), expiresAt, now);
        conversations.saveAndFlush(conversation);
        return new PublicAppointmentRequestReceipt(requestId,
                "Recibimos tu solicitud. Guarda la clave privada para volver a esta conversación. "
                        + "El horario solicitado es una preferencia y todavía no está confirmado.",
                token, "Bearer", expiresAt);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    @Override
    @Transactional
    public AppointmentRequestResponse reject(UUID actorId, UUID requestId) {
        User actor = actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (!isOpen(request)) {
            throw new ConflictException(REQUEST_STATE_NOT_ELIGIBLE,
                    "Cannot reject request while status is " + request.getStatus()
                            + "; eligible statuses are PENDING and PROPOSED");
        }
        request.reject(actor, clock.instant());
        if(auditService!=null)auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED,"APPOINTMENTS","AppointmentRequest",request.getId(),actorId);
        return mapper.toAdministrativeResponse(requests.saveAndFlush(request));
    }

    private Appointment createAppointment(AppointmentRequest request, User professional, Instant scheduledAt) {
        try {
            return request.getPatient() == null
                    ? appointments.createPublic(request.getRequesterFullName(), request.getRequesterPhone(),
                            professional.getId(), scheduledAt)
                    : appointments.create(request.getPatient().getId(), professional.getId(), scheduledAt);
        } catch (ConflictException exception) {
            if ("Appointment time is not available".equals(exception.getMessage())) {
                throw new ConflictException(APPOINTMENT_TIME_UNAVAILABLE,
                        "Cannot confirm appointment: the selected date and time is already occupied");
            }
            throw exception;
        }
    }

    private Patient patientFor(UUID userId) {
        if (userId == null) throw new BadRequestException("Authenticated user id is required");
        return patients.findByUser_Id(userId).orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }

    private User actor(UUID userId) {
        if (userId == null) throw new BadRequestException("Authenticated user id is required");
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private User dentist(UUID professionalId) {
        if (professionalId == null) throw new BadRequestException("Professional id is required");
        User professional = users.findWithRolesById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found"));
        boolean active = professional.getStatus() == UserStatus.ACTIVE && professional.getRoles().stream()
                .anyMatch(role -> role.isActive() && "DENTIST".equals(role.getCode()));
        if (!active) throw new ConflictException("Professional is not an active dentist");
        return professional;
    }

    private AppointmentRequest locked(UUID requestId) {
        return requests.findDetailedByIdForUpdate(requireId(requestId))
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
    }

    private AppointmentRequest lockedOwned(UUID requestId, UUID patientId) {
        AppointmentRequest request = locked(requestId);
        if (request.getPatient() == null || !request.getPatient().getId().equals(patientId)) {
            throw new ResourceNotFoundException("Appointment request not found");
        }
        return request;
    }

    private UUID requireId(UUID id) {
        if (id == null) throw new BadRequestException("Appointment request id is required");
        return id;
    }

    private void requireFuture(Instant value, String field) {
        if (value == null) throw new BadRequestException(field + " is required");
        if (!value.isAfter(clock.instant())) throw new BadRequestException(field + " must be in the future");
    }

    private PageRequest page(int page, int size) {
        if (page < 0) throw new BadRequestException("Page must be at least 0");
        if (size < 1) throw new BadRequestException("Size must be at least 1");
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), ORDER);
    }
}
