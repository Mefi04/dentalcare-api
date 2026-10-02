package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.UpdateWaitingRoomStatusRequest;
import com.dentalcare.api.modules.appointments.dto.response.WaitingRoomEntryResponse;
import com.dentalcare.api.modules.appointments.model.WaitingRoomStatus;
import com.dentalcare.api.modules.appointments.service.WaitingRoomService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Appointment waiting room")
public class WaitingRoomController {
    private final WaitingRoomService service;

    public WaitingRoomController(WaitingRoomService service) {
        this.service = service;
    }

    @GetMapping("/waiting-room")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY', 'DENTIST', 'ASSISTANT')")
    public ResponseEntity<Page<WaitingRoomEntryResponse>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) WaitingRoomStatus status,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findAll(date, status, professionalId, page, size));
    }

    @GetMapping("/{appointmentId}/waiting-room")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY', 'DENTIST', 'ASSISTANT')")
    public ResponseEntity<WaitingRoomEntryResponse> findByAppointment(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(service.findByAppointmentId(appointmentId));
    }

    @PostMapping("/{appointmentId}/waiting-room/check-in")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY', 'ASSISTANT')")
    public ResponseEntity<WaitingRoomEntryResponse> checkIn(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID appointmentId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.checkIn(principal.userId(), appointmentId));
    }

    @PatchMapping("/{appointmentId}/waiting-room/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY', 'ASSISTANT')")
    public ResponseEntity<WaitingRoomEntryResponse> advance(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID appointmentId,
            @Valid @RequestBody UpdateWaitingRoomStatusRequest request) {
        return ResponseEntity.ok(service.advance(principal.userId(), appointmentId, request.status()));
    }
}
