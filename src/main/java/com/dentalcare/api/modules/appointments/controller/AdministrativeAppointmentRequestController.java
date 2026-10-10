package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.LinkAppointmentRequestPatientRequest;
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
import com.dentalcare.api.modules.appointments.dto.request.VerifyPublicRequesterIdentityRequest;
import com.dentalcare.api.modules.patients.dto.request.CreatePatientRequest;

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

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<AppointmentRequestResponse> reject(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.reject(principal.userId(), requestId));
    }

    @PostMapping("/{requestId}/link-patient")
    @Operation(summary = "Link an existing patient record after in-person physical DPI verification on the confirmed appointment day")
    public ResponseEntity<AppointmentRequestResponse> linkPatient(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody LinkAppointmentRequestPatientRequest request) {
        return ResponseEntity.ok(service.linkPublicRequestPatient(
                principal.userId(), requestId, request.patientId()));
    }

    @PostMapping("/{requestId}/verify-requester-identity")
    @Operation(summary = "Record in-person physical DPI verification on the confirmed appointment day",
            description = "Only IN_PERSON is accepted for a public appointment. The public CUI, if submitted, is unverified and is not used to establish the link.")
    public ResponseEntity<AppointmentRequestResponse> verifyRequesterIdentity(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody VerifyPublicRequesterIdentityRequest request) {
        return ResponseEntity.ok(service.verifyPublicRequesterIdentity(
                principal.userId(), requestId, request));
    }

    @PostMapping("/{requestId}/register-patient")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY') and hasAuthority('PATIENT_CREATE')")
    @Operation(summary = "Create a new patient through the official patient service and link it atomically",
            description = "Requires a confirmed appointment on its clinic date, prior IN_PERSON physical DPI verification and PATIENT_CREATE. Body uses CreatePatientRequest; reception enters DPI, birthDate and gender from the in-person registration. Does not create portal access.")
    public ResponseEntity<AppointmentRequestResponse> registerPatient(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody CreatePatientRequest request) {
        return ResponseEntity.ok(service.registerAndLinkPublicRequester(
                principal.userId(), requestId, request));
    }

}
