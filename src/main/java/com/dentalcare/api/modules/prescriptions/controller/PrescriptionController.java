package com.dentalcare.api.modules.prescriptions.controller;

import com.dentalcare.api.modules.prescriptions.dto.request.CreatePrescriptionRequest;
import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionResponse;
import com.dentalcare.api.modules.prescriptions.service.PrescriptionService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PrescriptionController {
    private final PrescriptionService service;

    public PrescriptionController(PrescriptionService service) {
        this.service = service;
    }

    @Operation(summary = "List a patient's prescriptions")
    @GetMapping("/patients/{patientId}/prescriptions")
    @PreAuthorize("hasAuthority('PRESCRIPTION_READ')")
    public Page<PrescriptionResponse> list(@PathVariable UUID patientId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.findByPatient(patientId, page, size);
    }

    @Operation(summary = "Issue a prescription as the authenticated dentist")
    @PostMapping("/patients/{patientId}/prescriptions")
    @PreAuthorize("hasAuthority('PRESCRIPTION_CREATE')")
    public ResponseEntity<PrescriptionResponse> create(@PathVariable UUID patientId,
                                                        @Valid @RequestBody CreatePrescriptionRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        PrescriptionResponse response = service.create(patientId, principal.userId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/prescriptions/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get prescription detail")
    @GetMapping("/prescriptions/{id}")
    @PreAuthorize("hasAuthority('PRESCRIPTION_READ')")
    public PrescriptionResponse detail(@PathVariable UUID id) {
        return service.findById(id);
    }
}
