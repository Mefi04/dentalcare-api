package com.dentalcare.api.modules.treatments.controller;

import com.dentalcare.api.modules.treatments.dto.request.PrepareTreatmentConsentRequest;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentConsentResponse;
import com.dentalcare.api.modules.treatments.service.TreatmentConsentService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Treatment consents", description = "Versioned consent documents linked to real treatment plans")
public class TreatmentConsentController {
    private final TreatmentConsentService service;

    public TreatmentConsentController(TreatmentConsentService service) {
        this.service = service;
    }

    @PostMapping("/treatment-plans/{planId}/consents")
    @PreAuthorize("hasAuthority('TREATMENT_CONSENT_CREATE')")
    @Operation(summary = "Prepare an immutable consent document for an approved treatment plan")
    public ResponseEntity<TreatmentConsentResponse> prepare(
            @PathVariable UUID planId,
            @Valid @RequestBody PrepareTreatmentConsentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        TreatmentConsentResponse response = service.prepare(planId, principal.userId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/treatment-consents/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/treatment-plans/{planId}/consents")
    @PreAuthorize("hasAuthority('TREATMENT_CONSENT_READ')")
    @Operation(summary = "List consent history for a treatment plan")
    public ResponseEntity<List<TreatmentConsentResponse>> findByPlan(@PathVariable UUID planId) {
        return ResponseEntity.ok(service.findByPlan(planId));
    }

    @GetMapping("/treatment-consents/{consentId}")
    @PreAuthorize("hasAuthority('TREATMENT_CONSENT_READ')")
    @Operation(summary = "Get a treatment consent document")
    public ResponseEntity<TreatmentConsentResponse> findById(@PathVariable UUID consentId) {
        return ResponseEntity.ok(service.findById(consentId));
    }

    @PatchMapping("/treatment-consents/{consentId}/accept")
    @PreAuthorize("hasAuthority('TREATMENT_CONSENT_ACCEPT')")
    @Operation(summary = "Explicitly accept a pending treatment consent")
    public ResponseEntity<TreatmentConsentResponse> accept(
            @PathVariable UUID consentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(service.accept(consentId, principal.userId()));
    }

    @PatchMapping("/treatment-consents/{consentId}/revoke")
    @PreAuthorize("hasAuthority('TREATMENT_CONSENT_REVOKE')")
    @Operation(summary = "Revoke a pending or accepted treatment consent")
    public ResponseEntity<TreatmentConsentResponse> revoke(
            @PathVariable UUID consentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(service.revoke(consentId, principal.userId()));
    }
}
