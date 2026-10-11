package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.ProposeAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.request.ConfirmPublicAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.request.LinkAppointmentRequestPatientRequest;
import com.dentalcare.api.modules.appointments.dto.request.AssignAppointmentRequestProfessionalRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.service.AppointmentRequestService;
import com.dentalcare.api.modules.appointments.service.AppointmentAvailabilityService;
import com.dentalcare.api.modules.users.model.ProfessionalServiceCode;
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
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointment-requests")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY')")
@Tag(name = "Administrative appointment requests")
public class AdministrativeAppointmentRequestController {
    private final AppointmentRequestService service;
    private final AppointmentAvailabilityService availabilityService;

    public AdministrativeAppointmentRequestController(AppointmentRequestService service,
                                                      AppointmentAvailabilityService availabilityService) {
        this.service = service;
        this.availabilityService = availabilityService;
    }

    @GetMapping("/availability")
    @Operation(summary = "Get availability for an active dentist from the reception workflow",
            description = "Includes active dentists even when they do not have a public profile. Restricted to administrators and secretaries.")
    public ResponseEntity<AppointmentAvailabilityResponse> getAvailabilityForReception(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam UUID professionalId,
            @RequestParam(required = false, defaultValue = "GENERAL_DENTISTRY") ProfessionalServiceCode serviceCode) {
        return ResponseEntity.ok(availabilityService.getAdministrativeAvailability(date, professionalId, serviceCode));
    }

    @GetMapping
    @Operation(summary = "List and filter patient appointment requests",
            description = "Supports filtering by date range, patient, professional, status, and dual inbox source (PUBLIC vs PATIENT_PORTAL).")
    public ResponseEntity<Page<AppointmentRequestResponse>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) AppointmentRequestStatus status,
            @RequestParam(required = false) com.dentalcare.api.modules.appointments.model.AppointmentRequestSource source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findAll(from, to, patientId, professionalId, status, source, page, size));
    }

    @GetMapping("/public-inbox")
    @Operation(summary = "List public first-appointment requests for the reception inbox")
    public ResponseEntity<Page<AppointmentRequestResponse>> publicInbox(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) AppointmentRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findAll(from, to, null, professionalId, status,
                com.dentalcare.api.modules.appointments.model.AppointmentRequestSource.PUBLIC, page, size));
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
    public ResponseEntity<AppointmentRequestResponse> propose(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody ProposeAppointmentRequest request) {
        return ResponseEntity.ok(service.propose(
                principal.userId(), requestId, request.professionalId(), request.proposedAt()));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<AppointmentRequestResponse> reject(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.reject(principal.userId(), requestId));
    }

    @PostMapping("/{requestId}/link-patient")
    public ResponseEntity<AppointmentRequestResponse> linkPatient(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody LinkAppointmentRequestPatientRequest request) {
        return ResponseEntity.ok(service.linkPublicRequestPatient(
                principal.userId(), requestId, request.patientId()));
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

    @PostMapping("/{requestId}/confirm-public")
    @Operation(summary = "Atomically confirm a public first appointment at the time agreed with the caller",
            description = "Requires an earlier CONTACTED call attempt, an active dentist, and an available clinic slot. Creates the appointment and confirms the request in one transaction.")
    public ResponseEntity<AppointmentRequestResponse> confirmPublicAppointment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody ConfirmPublicAppointmentRequest request) {
        return ResponseEntity.ok(service.confirmPublicAppointment(principal.userId(), requestId, request));
    }

    @PostMapping("/{requestId}/contact-attempts")
    @Operation(summary = "Log a receptionist contact attempt for an appointment request",
            description = "Records the outcome and notes of a phone or messaging attempt to reach the patient.")
    public ResponseEntity<com.dentalcare.api.modules.appointments.dto.response.AppointmentContactAttemptResponse> logContactAttempt(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody com.dentalcare.api.modules.appointments.dto.request.CreateContactAttemptRequest request) {
        return ResponseEntity.ok(service.logContactAttempt(principal.userId(), requestId, request));
    }

    @GetMapping("/{requestId}/contact-attempts")
    @Operation(summary = "List contact attempts for an appointment request",
            description = "Returns paginated contact attempts ordered by most recent first.")
    public ResponseEntity<Page<com.dentalcare.api.modules.appointments.dto.response.AppointmentContactAttemptResponse>> findContactAttempts(
            @PathVariable UUID requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findContactAttempts(requestId, page, size));
    }
}
