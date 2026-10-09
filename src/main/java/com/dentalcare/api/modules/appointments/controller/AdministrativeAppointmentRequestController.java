package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.ProposeAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.request.LinkAppointmentRequestPatientRequest;
import com.dentalcare.api.modules.appointments.dto.request.AssignAppointmentRequestProfessionalRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.service.AppointmentRequestService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;
import java.time.LocalDate;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.dto.request.ClinicSchedulingMessageRequest;
import com.dentalcare.api.modules.appointments.dto.request.VerifyPublicRequesterIdentityRequest;
import com.dentalcare.api.modules.patients.dto.request.CreatePatientRequest;
import com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentConversationMessageRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentConversationMessagesPageResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestMessageResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentNotificationOutboxResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentWhatsAppDraftResponse;

@RestController
@RequestMapping("/api/v1/appointment-requests")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY')")
@Tag(name = "Administrative appointment requests")
public class AdministrativeAppointmentRequestController {
    private final AppointmentRequestService service;

    public AdministrativeAppointmentRequestController(AppointmentRequestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List and filter patient appointment requests")
    public ResponseEntity<Page<AppointmentRequestResponse>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) AppointmentRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findAll(from, to, patientId, professionalId, status, page, size));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<AppointmentRequestResponse> findById(@PathVariable UUID requestId) {
        return ResponseEntity.ok(service.findById(requestId));
    }

    @PostMapping("/{requestId}/accept")
    public ResponseEntity<AppointmentRequestResponse> accept(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.acceptRequestedTime(principal.userId(), requestId));
    }

    @PostMapping("/{requestId}/proposal")
    @Operation(summary = "Propose a real available appointment time to a public requester")
    public ResponseEntity<AppointmentRequestResponse> propose(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody ProposeAppointmentRequest request) {
        return ResponseEntity.ok(service.propose(
                principal.userId(), requestId, request.professionalId(), request.proposedAt()));
    }

    @GetMapping("/{requestId}/availability")
    @Operation(summary = "Read a dentist's scheduled times for one clinic date; proposal validates again")
    public ResponseEntity<AppointmentAvailabilityResponse> availability(@PathVariable UUID requestId,
            @RequestParam UUID professionalId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.findById(requestId);
        return ResponseEntity.ok(service.getAvailability(professionalId, date));
    }

    @PostMapping("/{requestId}/messages")
    @Operation(summary = "Send a predefined non-clinical scheduling message into a public conversation")
    public ResponseEntity<AppointmentRequestResponse> addMessage(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody ClinicSchedulingMessageRequest request) {
        return ResponseEntity.ok(service.addSchedulingMessage(principal.userId(), requestId, request));
    }

    @GetMapping("/{requestId}/conversation/messages")
    @Operation(summary = "Read a paginated public conversation as authorized reception")
    public ResponseEntity<AppointmentConversationMessagesPageResponse> conversationMessages(
            @PathVariable UUID requestId, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getAdministrativeMessages(requestId, cursor, size));
    }

    @PostMapping("/{requestId}/conversation/messages")
    @Operation(summary = "Send a non-clinical free-text message from reception", description = "Sender is derived from the authenticated administrator/secretary. Requires Idempotency-Key UUID.")
    public ResponseEntity<AppointmentRequestMessageResponse> addConversationMessage(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID key,
            @Valid @RequestBody CreateAppointmentConversationMessageRequest request) {
        return ResponseEntity.ok(service.addAdministrativeMessage(principal.userId(), requestId, key, request));
    }

    @GetMapping("/{requestId}/notifications")
    @Operation(summary = "Read safe delivery status and retries for a public appointment request")
    public ResponseEntity<java.util.List<AppointmentNotificationOutboxResponse>> notifications(
            @PathVariable UUID requestId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getNotificationStatus(requestId, page, size));
    }

    @GetMapping("/{requestId}/whatsapp-draft")
    @Operation(summary = "Prepare but do not send a WhatsApp message for reception to review manually")
    public ResponseEntity<AppointmentWhatsAppDraftResponse> whatsappDraft(@PathVariable UUID requestId) {
        return ResponseEntity.ok(service.createWhatsAppDraft(requestId));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<AppointmentRequestResponse> reject(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.reject(principal.userId(), requestId));
    }

    @PostMapping("/{requestId}/link-patient")
    @Operation(summary = "Link an existing patient record after reception verifies requester identity")
    public ResponseEntity<AppointmentRequestResponse> linkPatient(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody LinkAppointmentRequestPatientRequest request) {
        return ResponseEntity.ok(service.linkPublicRequestPatient(
                principal.userId(), requestId, request.patientId()));
    }

    @PostMapping("/{requestId}/verify-requester-identity")
    @Operation(summary = "Record reception's identity verification before linking or registering a patient",
            description = "Administrative attestation only; CUI match or public-channel OTP alone does not establish the link. Methods: IN_PERSON, CALLBACK_TO_REGISTERED_CONTACT, DOCUMENT_REVIEW.")
    public ResponseEntity<AppointmentRequestResponse> verifyRequesterIdentity(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody VerifyPublicRequesterIdentityRequest request) {
        return ResponseEntity.ok(service.verifyPublicRequesterIdentity(
                principal.userId(), requestId, request));
    }

    @PostMapping("/{requestId}/register-patient")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY') and hasAuthority('PATIENT_CREATE')")
    @Operation(summary = "Create a new patient through the official patient service and link it atomically",
            description = "Requires a prior identity-verification record and PATIENT_CREATE. Body uses CreatePatientRequest; all required administrative fields (including DPI, birthDate and gender) must be supplied by reception. Does not create portal access.")
    public ResponseEntity<AppointmentRequestResponse> registerPatient(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody CreatePatientRequest request) {
        return ResponseEntity.ok(service.registerAndLinkPublicRequester(
                principal.userId(), requestId, request));
    }

    @PostMapping("/{requestId}/assign-professional")
    @Operation(summary = "Assign an active dentist to a public appointment request",
            description = "Reception may assign or reassign a dentist on an open public request. This does not confirm or reserve an appointment. 409 codes distinguish an ineligible request state (APPOINTMENT_REQUEST_STATE_NOT_ELIGIBLE), a non-public request (APPOINTMENT_REQUEST_NOT_PUBLIC), and an unavailable dentist (PROFESSIONAL_NOT_AVAILABLE).")
    public ResponseEntity<AppointmentRequestResponse> assignProfessional(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody AssignAppointmentRequestProfessionalRequest request) {
        return ResponseEntity.ok(service.assignPublicRequestProfessional(
                principal.userId(), requestId, request.professionalId()));
    }

    @PostMapping("/{requestId}/confirm-public-proposal")
    public ResponseEntity<AppointmentRequestResponse> confirmPublicProposal(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.confirmPublicProposal(principal.userId(), requestId));
    }
}
