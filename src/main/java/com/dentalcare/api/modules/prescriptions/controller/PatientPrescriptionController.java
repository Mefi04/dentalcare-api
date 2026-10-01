package com.dentalcare.api.modules.prescriptions.controller;

import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionResponse;
import com.dentalcare.api.modules.prescriptions.service.PrescriptionService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/me/prescriptions")
@PreAuthorize("hasRole('PATIENT')")
public class PatientPrescriptionController {
    private final PrescriptionService service;

    public PatientPrescriptionController(PrescriptionService service) {
        this.service = service;
    }

    @Operation(summary = "List the authenticated patient's prescriptions")
    @GetMapping
    public Page<PrescriptionResponse> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.findMine(principal.userId(), page, size);
    }

    @Operation(summary = "Get one prescription owned by the authenticated patient")
    @GetMapping("/{id}")
    public PrescriptionResponse detail(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable UUID id) {
        return service.findMineById(principal.userId(), id);
    }
}
