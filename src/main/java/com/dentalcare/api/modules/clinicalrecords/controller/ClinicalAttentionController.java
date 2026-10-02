package com.dentalcare.api.modules.clinicalrecords.controller;

import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalAttentionRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDiagnosisRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalPreparationRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateEvolutionNoteRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDiagnosisResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalEvolutionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalPreparationResponse;
import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalRecordService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Clinical attentions", description = "Clinical attention encounters, structured diagnoses, evolution notes, and pre-attention preparation")
public class ClinicalAttentionController {

    private final ClinicalRecordService clinicalRecordService;

    public ClinicalAttentionController(ClinicalRecordService clinicalRecordService) {
        this.clinicalRecordService = clinicalRecordService;
    }

    @PostMapping("/patients/{patientId}/clinical-attentions")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register a new clinical attention for a patient")
    public ResponseEntity<ClinicalAttentionResponse> createAttention(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateClinicalAttentionRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClinicalAttentionResponse response = clinicalRecordService.createAttention(
                patientId, request, principal.userId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/patients/{patientId}/clinical-attentions/{id}")
                .buildAndExpand(patientId, response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/patients/{patientId}/clinical-attentions")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "List paginated clinical attentions for a patient")
    public ResponseEntity<Page<ClinicalAttentionResponse>> getAttentions(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalRecordService.findAttentionsByPatient(patientId, page, size));
    }

    @GetMapping("/clinical-attentions/{attentionId}")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get clinical attention details by id")
    public ResponseEntity<ClinicalAttentionResponse> getAttentionById(@PathVariable UUID attentionId) {
        return ResponseEntity.ok(clinicalRecordService.findAttentionById(attentionId));
    }

    @GetMapping("/patients/{patientId}/clinical-attentions/{attentionId}")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get clinical attention details by id with anti-IDOR patient verification")
    public ResponseEntity<ClinicalAttentionResponse> getPatientAttentionById(
            @PathVariable UUID patientId,
            @PathVariable UUID attentionId) {
        return ResponseEntity.ok(clinicalRecordService.findAttentionById(patientId, attentionId));
    }

    @PostMapping("/clinical-attentions/{attentionId}/diagnoses")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register a diagnosis linked to a clinical attention")
    public ResponseEntity<ClinicalDiagnosisResponse> createDiagnosis(
            @PathVariable UUID attentionId,
            @Valid @RequestBody CreateClinicalDiagnosisRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClinicalDiagnosisResponse response = clinicalRecordService.createDiagnosis(
                attentionId, request, principal.userId());
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/patients/{patientId}/clinical-attentions/{attentionId}/diagnoses")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register a diagnosis linked to a clinical attention with patient verification")
    public ResponseEntity<ClinicalDiagnosisResponse> createPatientDiagnosis(
            @PathVariable UUID patientId,
            @PathVariable UUID attentionId,
            @Valid @RequestBody CreateClinicalDiagnosisRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClinicalDiagnosisResponse response = clinicalRecordService.createDiagnosis(
                patientId, attentionId, request, principal.userId());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/patients/{patientId}/diagnoses")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "List paginated diagnoses for a patient")
    public ResponseEntity<Page<ClinicalDiagnosisResponse>> getDiagnoses(
            @PathVariable UUID patientId,
            @RequestParam(required = false) DiagnosisType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalRecordService.findDiagnosesByPatient(patientId, type, page, size));
    }

    @GetMapping("/patients/{patientId}/diagnoses/{diagnosisId}")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get diagnosis details by id with patient verification")
    public ResponseEntity<ClinicalDiagnosisResponse> getDiagnosisById(
            @PathVariable UUID patientId,
            @PathVariable UUID diagnosisId) {
        return ResponseEntity.ok(clinicalRecordService.findDiagnosisById(patientId, diagnosisId));
    }

    @PostMapping("/clinical-attentions/{attentionId}/evolution")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register an evolution note linked to a clinical attention")
    public ResponseEntity<ClinicalEvolutionResponse> createEvolutionNote(
            @PathVariable UUID attentionId,
            @Valid @RequestBody CreateEvolutionNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClinicalEvolutionResponse response = clinicalRecordService.createEvolutionNote(
                attentionId, request, principal.userId());
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/patients/{patientId}/clinical-attentions/{attentionId}/evolution")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register an evolution note linked to a clinical attention with patient verification")
    public ResponseEntity<ClinicalEvolutionResponse> createPatientEvolutionNote(
            @PathVariable UUID patientId,
            @PathVariable UUID attentionId,
            @Valid @RequestBody CreateEvolutionNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClinicalEvolutionResponse response = clinicalRecordService.createEvolutionNote(
                patientId, attentionId, request, principal.userId());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/patients/{patientId}/evolution")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "List paginated evolution notes for a patient")
    public ResponseEntity<Page<ClinicalEvolutionResponse>> getEvolutionNotes(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalRecordService.findEvolutionNotesByPatient(patientId, page, size));
    }

    @GetMapping("/patients/{patientId}/evolution/{evolutionId}")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get evolution note details by id with patient verification")
    public ResponseEntity<ClinicalEvolutionResponse> getEvolutionNoteById(
            @PathVariable UUID patientId,
            @PathVariable UUID evolutionId) {
        return ResponseEntity.ok(clinicalRecordService.findEvolutionNoteById(patientId, evolutionId));
    }

    @PostMapping("/patients/{patientId}/preparations")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register clinical pre-attention preparation and vital signs for a patient")
    public ResponseEntity<ClinicalPreparationResponse> createPreparation(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateClinicalPreparationRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClinicalPreparationResponse response = clinicalRecordService.createPreparation(
                patientId, null, request, principal.userId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/patients/{patientId}/preparations/{id}")
                .buildAndExpand(patientId, response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/patients/{patientId}/clinical-attentions/{attentionId}/preparation")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_WRITE')")
    @Operation(summary = "Register clinical preparation linked to a specific clinical attention")
    public ResponseEntity<ClinicalPreparationResponse> createPreparationForAttention(
            @PathVariable UUID patientId,
            @PathVariable UUID attentionId,
            @Valid @RequestBody CreateClinicalPreparationRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClinicalPreparationResponse response = clinicalRecordService.createPreparation(
                patientId, attentionId, request, principal.userId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/patients/{patientId}/preparations/{id}")
                .buildAndExpand(patientId, response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/patients/{patientId}/preparations")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "List paginated clinical preparations for a patient")
    public ResponseEntity<Page<ClinicalPreparationResponse>> getPreparations(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(clinicalRecordService.findPreparationsByPatient(patientId, page, size));
    }

    @GetMapping("/patients/{patientId}/preparations/latest")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get latest clinical preparation record for a patient")
    public ResponseEntity<ClinicalPreparationResponse> getLatestPreparation(@PathVariable UUID patientId) {
        return ResponseEntity.ok(clinicalRecordService.findLatestPreparationByPatient(patientId));
    }

    @GetMapping("/patients/{patientId}/preparations/{preparationId}")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get clinical preparation record by id with patient verification")
    public ResponseEntity<ClinicalPreparationResponse> getPreparationById(
            @PathVariable UUID patientId,
            @PathVariable UUID preparationId) {
        return ResponseEntity.ok(clinicalRecordService.findPreparationById(patientId, preparationId));
    }

    @GetMapping("/patients/{patientId}/clinical-attentions/{attentionId}/preparation")
    @PreAuthorize("hasAuthority('CLINICAL_RECORD_READ')")
    @Operation(summary = "Get clinical preparation record linked to a clinical attention")
    public ResponseEntity<ClinicalPreparationResponse> getPreparationByAttention(
            @PathVariable UUID patientId,
            @PathVariable UUID attentionId) {
        return ResponseEntity.ok(clinicalRecordService.findPreparationByAttention(patientId, attentionId));
    }
}
