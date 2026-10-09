package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.CreatePublicAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentRequestReceipt;
import com.dentalcare.api.modules.appointments.service.AppointmentRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/appointment-requests")
@Tag(name = "Public appointment requests")
public class PublicAppointmentRequestController {
    private final AppointmentRequestService service;

    public PublicAppointmentRequestController(AppointmentRequestService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Request a first appointment without an account",
            description = "Returns a one-time, seven-day conversation bearer token; save it because it cannot be recovered. A retry with the same Idempotency-Key and payload rotates and returns a new token without persisting token material in plaintext. requestedAt is a preference, not a confirmed or reserved appointment. No email/SMS verification is required.")
    public ResponseEntity<PublicAppointmentRequestReceipt> create(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreatePublicAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.createPublic(request, idempotencyKey));
    }
}
