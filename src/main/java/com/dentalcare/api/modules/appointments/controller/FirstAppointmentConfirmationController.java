package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.ConfirmFirstAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.service.FirstAppointmentConfirmationService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointment-requests/{requestId}/telephone-confirmation")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY')")
public class FirstAppointmentConfirmationController {
    private final FirstAppointmentConfirmationService service;

    public FirstAppointmentConfirmationController(FirstAppointmentConfirmationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Confirm a first appointment after telephone agreement")
    public ResponseEntity<AppointmentRequestResponse> confirm(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID requestId,
            @Valid @RequestBody ConfirmFirstAppointmentRequest input) {
        return ResponseEntity.ok(service.confirm(actor.userId(), requestId, input));
    }
}
