package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.FirstAppointmentIntakeRequest;
import com.dentalcare.api.modules.appointments.dto.response.FirstAppointmentReceipt;
import com.dentalcare.api.modules.appointments.service.PublicFirstAppointmentService;
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
    private final PublicFirstAppointmentService service;

    public PublicAppointmentRequestController(PublicFirstAppointmentService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Submit a first-appointment request for general dentistry",
            description = "Returns 202 with an acknowledgement. The preferred time is not reserved; reception calls to confirm. Idempotency-Key must be a UUID.")
    public ResponseEntity<FirstAppointmentReceipt> create(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody FirstAppointmentIntakeRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.submit(request, idempotencyKey));
    }
}
