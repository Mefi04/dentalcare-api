package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.ProposeAppointmentRequest;
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
}
