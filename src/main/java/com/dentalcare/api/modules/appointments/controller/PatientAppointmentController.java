package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.request.CreatePatientAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentProfessionalResponse;
import com.dentalcare.api.modules.appointments.dto.response.PatientAppointmentResponse;
import com.dentalcare.api.modules.appointments.service.PatientAppointmentService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/me/appointments")
@Tag(name = "Patient appointments", description = "Authenticated patient appointment self-service")
@PreAuthorize("hasRole('PATIENT')")
public class PatientAppointmentController {
    private final PatientAppointmentService patientAppointmentService;

    public PatientAppointmentController(PatientAppointmentService patientAppointmentService) {
        this.patientAppointmentService = patientAppointmentService;
    }

    @Operation(summary = "List the authenticated patient's appointments")
    @GetMapping
    public ResponseEntity<Page<PatientAppointmentResponse>> findCurrentPatientAppointments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(patientAppointmentService.findCurrentPatientAppointments(
                principal.userId(), page, size));
    }

    @Operation(summary = "Get one appointment owned by the authenticated patient")
    @GetMapping("/{appointmentId}")
    public ResponseEntity<PatientAppointmentResponse> findCurrentPatientAppointment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(patientAppointmentService.findCurrentPatientAppointment(
                principal.userId(), appointmentId));
    }

    @Operation(summary = "Schedule an appointment for the authenticated patient")
    @PostMapping
    public ResponseEntity<PatientAppointmentResponse> createCurrentPatientAppointment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreatePatientAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                patientAppointmentService.createCurrentPatientAppointment(
                        principal.userId(), request.professionalId(), request.scheduledAt()));
    }

    @Operation(summary = "Cancel an appointment owned by the authenticated patient")
    @PatchMapping("/{appointmentId}/cancel")
    public ResponseEntity<PatientAppointmentResponse> cancelCurrentPatientAppointment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(patientAppointmentService.cancelCurrentPatientAppointment(
                principal.userId(), appointmentId));
    }

    @Operation(summary = "List dentists available for patient appointment booking")
    @GetMapping("/professionals")
    public ResponseEntity<List<AppointmentProfessionalResponse>> findAvailableProfessionals() {
        return ResponseEntity.ok(patientAppointmentService.findAvailableProfessionals());
    }
}
