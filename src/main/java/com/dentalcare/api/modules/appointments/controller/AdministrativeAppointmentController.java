package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.CreateAdministrativeAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.request.RescheduleAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.request.UpdateAppointmentStatusRequest;
import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.service.AdministrativeAppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Administrative appointments", description = "Administrative appointment scheduling and status management")
public class AdministrativeAppointmentController {

    private final AdministrativeAppointmentService administrativeAppointmentService;

    public AdministrativeAppointmentController(AdministrativeAppointmentService administrativeAppointmentService) {
        this.administrativeAppointmentService = administrativeAppointmentService;
    }

    @Operation(summary = "List and filter appointments for the administrative agenda")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY', 'DENTIST', 'ASSISTANT')")
    public ResponseEntity<Page<AdministrativeAppointmentResponse>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(administrativeAppointmentService.findAll(
                from, to, patientId, professionalId, status, page, size));
    }

    @Operation(summary = "Get an appointment for the administrative agenda")
    @GetMapping("/{appointmentId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY', 'DENTIST', 'ASSISTANT')")
    public ResponseEntity<AdministrativeAppointmentResponse> findById(@PathVariable UUID appointmentId) {
        return ResponseEntity.ok(administrativeAppointmentService.findById(appointmentId));
    }

    @Operation(summary = "Create an appointment for an existing patient")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY')")
    public ResponseEntity<AdministrativeAppointmentResponse> create(
            @Valid @RequestBody CreateAdministrativeAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(administrativeAppointmentService.create(request));
    }

    @Operation(summary = "Reschedule an appointment")
    @PatchMapping("/{appointmentId}/schedule")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY')")
    public ResponseEntity<AdministrativeAppointmentResponse> reschedule(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody RescheduleAppointmentRequest request) {
        return ResponseEntity.ok(administrativeAppointmentService.reschedule(
                appointmentId, request.scheduledAt()));
    }

    @Operation(summary = "Change an appointment status")
    @PatchMapping("/{appointmentId}/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY', 'DENTIST', 'ASSISTANT')")
    public ResponseEntity<AdministrativeAppointmentResponse> updateStatus(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return ResponseEntity.ok(administrativeAppointmentService.updateStatus(
                appointmentId, request.status()));
    }
}
