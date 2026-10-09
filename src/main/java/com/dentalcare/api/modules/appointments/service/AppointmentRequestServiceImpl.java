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
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
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

@Service
public class AppointmentRequestServiceImpl implements AppointmentRequestService {
    @org.springframework.beans.factory.annotation.Autowired(required = false) private AuditService auditService;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final List<AppointmentRequestStatus> ACTIVE_PUBLIC_STATUSES =
            List.of(AppointmentRequestStatus.PENDING, AppointmentRequestStatus.PROPOSED);

    private final AppointmentRequestRepository requests;
    private final PatientRepository patients;
    private final UserRepository users;
    private final AppointmentService appointments;
    private final AppointmentRequestMapper mapper;
    private final Clock clock;

    public AppointmentRequestServiceImpl(AppointmentRequestRepository requests, PatientRepository patients,
                                         UserRepository users, AppointmentService appointments,
                                         AppointmentRequestMapper mapper, Clock clock) {
        this.requests = requests;
        this.patients = patients;
        this.users = users;
        this.appointments = appointments;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PublicAppointmentRequestReceipt createPublic(CreatePublicAppointmentRequest request, UUID idempotencyKey) {
        if (idempotencyKey == null) throw new BadRequestException("Idempotency-Key header is required");
        requireFuture(request.requestedAt(), "Requested date and time");

        String fullName = request.fullName().trim().replaceAll("\\s+", " ");
        String cui = request.cui() == null ? null : request.cui().trim();
        String phone = request.phone().trim();
        long phoneDigits = phone.chars().filter(Character::isDigit).count();
        if (phoneDigits < 7 || phoneDigits > 15) {
            throw new BadRequestException("Phone number must contain between 7 and 15 digits");
        }
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        String reason = request.reason() == null || request.reason().isBlank()
                ? null : request.reason().trim();
        String payloadHash = publicPayloadHash(fullName, cui, phone, email, request.requestedAt(),
                request.professionalId(), reason);

        var repeated = requests.findByIdempotencyKey(idempotencyKey);
        if (repeated.isPresent()) {
            if (!payloadHash.equals(repeated.get().getIdempotencyPayloadHash())) {
                throw new ConflictException("Idempotency key was already used with different request data");
            }
            return publicReceipt(repeated.get().getId());
        }

        User professional = request.professionalId() == null ? null : publicDentist(request.professionalId());
        if (cui != null && hasActiveEquivalent(cui, request.requestedAt(), request.professionalId())) {
            throw new ConflictException("An equivalent appointment request is already active");
        }

        Patient patient = cui == null ? null : patients.findByDpi(cui).orElse(null);
        Instant now = clock.instant();
        AppointmentRequest created = new AppointmentRequest(UUID.randomUUID(), patient, professional,
                request.requestedAt(), AppointmentRequestStatus.PENDING, now, now,
                fullName, cui, phone, email, reason, idempotencyKey, payloadHash);
        AppointmentRequest saved = requests.saveAndFlush(created);
        if (auditService != null) {
            auditService.success(AuditActions.APPOINTMENT_REQUEST_CREATED, "APPOINTMENTS",
                    "PublicAppointmentRequest", saved.getId(), null);
        }
        return publicReceipt(saved.getId());
    }

    @Override
    @Transactional
    public AppointmentRequestResponse linkPublicRequestPatient(UUID actorId, UUID requestId, UUID patientId) {
        actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (request.getRequesterFullName() == null) {
            throw new ConflictException("Only a public appointment request can be linked this way");
        }
        if (request.getStatus() != AppointmentRequestStatus.PENDING
                && request.getStatus() != AppointmentRequestStatus.PROPOSED) {
            throw new ConflictException("Closed appointment requests cannot be linked to a patient");
        }
        if (request.getPatient() != null) {
            if (request.getPatient().getId().equals(patientId)) return mapper.toResponse(request);
            throw new ConflictException("Appointment request is already linked to a patient");
        }
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        if (request.getRequesterCui() != null && !request.getRequesterCui().equals(patient.getDpi())) {
            throw new ConflictException("Patient DPI does not match the public request");
        }
        request.linkPatient(patient);
        if (auditService != null) {
            auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED, "APPOINTMENTS",
                    "AppointmentRequest", request.getId(), actorId);
        }
        return mapper.toResponse(requests.saveAndFlush(request));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse assignPublicRequestProfessional(UUID actorId, UUID requestId, UUID professionalId) {
        actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (request.getRequesterFullName() == null) {
            throw new ConflictException("Only a public appointment request can be assigned this way");
        }
        if (request.getStatus() != AppointmentRequestStatus.PENDING
                && request.getStatus() != AppointmentRequestStatus.PROPOSED) {
            throw new ConflictException("Closed appointment requests cannot be reassigned");
        }
        User professional = dentist(professionalId);
        request.assignProfessional(professional, clock.instant());
        if (auditService != null) {
            auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED, "APPOINTMENTS",
                    "AppointmentRequest", request.getId(), actorId);
        }
        return mapper.toResponse(requests.saveAndFlush(request));
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
            throw new ConflictException("Link the public requester to a patient record before confirming");
        }
        if (request.getStatus() != AppointmentRequestStatus.PROPOSED) {
            throw new ConflictException("Only a proposed public appointment request can be confirmed");
        }
        Appointment appointment = appointments.create(request.getPatient().getId(),
                request.getProposedProfessional().getId(), request.getProposedAt());
        request.confirm(appointment, actor, clock.instant());
        if (auditService != null) {
            auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED, "APPOINTMENTS",
                    "AppointmentRequest", request.getId(), actorId);
        }
        return mapper.toResponse(requests.saveAndFlush(request));
    }

    private boolean hasActiveEquivalent(String cui, Instant requestedAt, UUID professionalId) {
        if (professionalId == null) {
            return requests.existsByRequesterCuiAndRequestedAtAndRequestedProfessionalIsNullAndStatusIn(
                    cui, requestedAt, ACTIVE_PUBLIC_STATUSES);
        }
        return requests.existsByRequesterCuiAndRequestedAtAndRequestedProfessional_IdAndStatusIn(
                cui, requestedAt, professionalId, ACTIVE_PUBLIC_STATUSES);
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

    private PublicAppointmentRequestReceipt publicReceipt(UUID requestId) {
        return new PublicAppointmentRequestReceipt(requestId,
                "We received your request. The clinic will contact you to confirm or propose a time.");
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
    @Transactional(readOnly = true)
    public Page<AppointmentRequestResponse> findAll(Instant from, Instant to, UUID patientId,
                                                     UUID professionalId, AppointmentRequestStatus status,
                                                     int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("From date must not be after to date");
        }
        Specification<AppointmentRequest> spec = Specification.unrestricted();
        if (from != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("requestedAt"), from));
        if (to != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("requestedAt"), to));
        if (patientId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("patient").get("id"), patientId));
        if (professionalId != null) spec = spec.and((root, query, cb) -> cb.or(
                cb.equal(root.get("requestedProfessional").get("id"), professionalId),
                cb.equal(root.get("proposedProfessional").get("id"), professionalId)));
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return requests.findAll(spec, page(page, size)).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentRequestResponse findById(UUID requestId) {
        return requests.findDetailedById(requireId(requestId)).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse acceptRequestedTime(UUID actorId, UUID requestId) {
        User actor = actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (request.getStatus() == AppointmentRequestStatus.CONFIRMED) return mapper.toResponse(request);
        if (request.getStatus() != AppointmentRequestStatus.PENDING) {
            throw new ConflictException("Only a pending appointment request can be accepted by the clinic");
        }
        if (request.getPatient() == null) {
            throw new ConflictException("Link the requester to a patient record before confirming the appointment");
        }
        if (request.getRequestedProfessional() == null) {
            throw new ConflictException("No preferred professional was selected; the clinic must propose a time and professional");
        }
        Appointment appointment = appointments.create(request.getPatient().getId(),
                request.getRequestedProfessional().getId(), request.getRequestedAt());
        request.confirm(appointment, actor, clock.instant());
        if(auditService!=null)auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED,"APPOINTMENTS","AppointmentRequest",request.getId(),actorId);
        return mapper.toResponse(requests.saveAndFlush(request));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse propose(UUID actorId, UUID requestId, UUID professionalId, Instant proposedAt) {
        User actor = actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (request.getStatus() != AppointmentRequestStatus.PENDING
                && request.getStatus() != AppointmentRequestStatus.PROPOSED) {
            throw new ConflictException("Closed appointment requests cannot receive a proposal");
        }
        requireFuture(proposedAt, "Proposed date and time");
        User professional = professionalId == null ? request.getRequestedProfessional() : dentist(professionalId);
        if (professional == null) {
            throw new ConflictException("A professional must be selected for the clinic proposal");
        }
        request.propose(professional, proposedAt, actor, clock.instant());
        if(auditService!=null)auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED,"APPOINTMENTS","AppointmentRequest",request.getId(),actorId);
        return mapper.toResponse(requests.saveAndFlush(request));
    }

    @Override
    @Transactional
    public AppointmentRequestResponse reject(UUID actorId, UUID requestId) {
        User actor = actor(actorId);
        AppointmentRequest request = locked(requestId);
        if (request.getStatus() != AppointmentRequestStatus.PENDING
                && request.getStatus() != AppointmentRequestStatus.PROPOSED) {
            throw new ConflictException("Closed appointment requests cannot be rejected");
        }
        request.reject(actor, clock.instant());
        if(auditService!=null)auditService.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED,"APPOINTMENTS","AppointmentRequest",request.getId(),actorId);
        return mapper.toResponse(requests.saveAndFlush(request));
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
