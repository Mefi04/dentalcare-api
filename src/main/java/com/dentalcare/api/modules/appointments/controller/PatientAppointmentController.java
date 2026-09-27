package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.response.PatientAppointmentResponse;
import com.dentalcare.api.modules.appointments.service.PatientAppointmentService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
