package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentContactAttemptRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentContactAttemptResponse;
import com.dentalcare.api.modules.appointments.service.AppointmentContactService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointment-requests/{requestId}/contact-attempts")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY')")
@Tag(name = "Administrative appointment contacts")
public class AppointmentContactController {
    private final AppointmentContactService service;

    public AppointmentContactController(AppointmentContactService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Record a telephone contact attempt for a pending public request")
    public ResponseEntity<AppointmentContactAttemptResponse> record(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID requestId,
            @Valid @RequestBody CreateAppointmentContactAttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.record(principal.userId(), requestId, request));
    }

    @GetMapping
    @Operation(summary = "Read paginated telephone contact history")
    public ResponseEntity<Page<AppointmentContactAttemptResponse>> history(@PathVariable UUID requestId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.history(requestId, page, size));
    }
}
