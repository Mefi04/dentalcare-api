package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.modules.treatments.dto.request.CompleteTreatmentProcedureRequest;
import com.dentalcare.api.modules.treatments.dto.request.CreateTreatmentProcedureRequest;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentProcedureResponse;
import com.dentalcare.api.modules.treatments.service.TreatmentProcedureService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Treatment procedures", description = "Persistent treatment procedure execution and history")
public class TreatmentProcedureController {
    private final TreatmentProcedureService service;

    public TreatmentProcedureController(TreatmentProcedureService service) {
        this.service = service;
    }

    @PostMapping("/treatment-plans/{planId}/procedures")
    @PreAuthorize("hasAuthority('TREATMENT_PROCEDURE_EXECUTE')")
    @Operation(summary = "Start one planned treatment procedure")
    public ResponseEntity<TreatmentProcedureResponse> register(
            @PathVariable UUID planId,
            @Valid @RequestBody CreateTreatmentProcedureRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        TreatmentProcedureResponse response = service.register(planId, principal.userId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/treatment-procedures/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/treatment-procedures/{procedureId}/complete")
    @PreAuthorize("hasAuthority('TREATMENT_PROCEDURE_COMPLETE')")
    @Operation(summary = "Complete an in-progress treatment procedure")
    public ResponseEntity<TreatmentProcedureResponse> complete(
            @PathVariable UUID procedureId,
            @Valid @RequestBody(required = false) CompleteTreatmentProcedureRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(service.complete(procedureId, principal.userId(), request));
    }

    @GetMapping("/treatment-procedures/{procedureId}")
    @PreAuthorize("hasAuthority('TREATMENT_PROCEDURE_READ')")
    @Operation(summary = "Get treatment procedure execution detail")
    public ResponseEntity<TreatmentProcedureResponse> findById(@PathVariable UUID procedureId) {
        return ResponseEntity.ok(service.findById(procedureId));
    }

    @GetMapping("/treatment-plans/{planId}/procedures")
    @PreAuthorize("hasAuthority('TREATMENT_PROCEDURE_READ')")
    @Operation(summary = "List a treatment plan's procedure execution history")
    public ResponseEntity<Page<TreatmentProcedureResponse>> findByPlan(
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findByPlan(planId, page, size));
    }

    @GetMapping("/patients/{patientId}/treatment-procedures")
    @PreAuthorize("hasAuthority('TREATMENT_PROCEDURE_READ')")
    @Operation(summary = "List a patient's treatment procedure history")
    public ResponseEntity<Page<TreatmentProcedureResponse>> findByPatient(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findByPatient(patientId, page, size));
    }
}
