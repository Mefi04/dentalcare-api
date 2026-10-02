package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.service.AppointmentRequestService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/me/appointment-requests")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Patient appointment requests")
public class PatientAppointmentRequestController {
    private final AppointmentRequestService service;

    public PatientAppointmentRequestController(AppointmentRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AppointmentRequestResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createForPatient(principal.userId(), request.professionalId(), request.requestedAt()));
    }

    @GetMapping
    public ResponseEntity<Page<AppointmentRequestResponse>> findAll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findForPatient(principal.userId(), page, size));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<AppointmentRequestResponse> findById(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.findOwned(principal.userId(), requestId));
    }

    @PostMapping("/{requestId}/accept-proposal")
    public ResponseEntity<AppointmentRequestResponse> acceptProposal(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.acceptProposal(principal.userId(), requestId));
    }

    @PostMapping("/{requestId}/reject-proposal")
    public ResponseEntity<AppointmentRequestResponse> rejectProposal(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.rejectProposal(principal.userId(), requestId));
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<AppointmentRequestResponse> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.cancelForPatient(principal.userId(), requestId));
    }
}
