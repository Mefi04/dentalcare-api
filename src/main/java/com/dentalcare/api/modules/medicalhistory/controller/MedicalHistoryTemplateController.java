package com.dentalcare.api.modules.medicalhistory.controller;

import com.dentalcare.api.modules.medicalhistory.dto.request.CreateMedicalHistoryTemplateRequest;
import com.dentalcare.api.modules.medicalhistory.dto.request.CreateMedicalHistoryTemplateVersionRequest;
import com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryTemplateResponse;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryWorkflowService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/medical-history")
@Tag(name="Medical history questionnaires",description="Versioned medical history questionnaire templates")
public class MedicalHistoryTemplateController {
    private final MedicalHistoryWorkflowService service;
    public MedicalHistoryTemplateController(MedicalHistoryWorkflowService service){this.service=service;}
    @PostMapping("/templates") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_TEMPLATE_MANAGE')")
    @Operation(summary="Create a draft versioned questionnaire template")
    public ResponseEntity<MedicalHistoryTemplateResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,@Valid @RequestBody CreateMedicalHistoryTemplateRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.createTemplate(request,principal.userId()));}
    @PostMapping("/templates/{templateId}/versions") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_TEMPLATE_MANAGE')")
    @Operation(summary="Create a new draft version of an existing questionnaire template")
    public ResponseEntity<MedicalHistoryTemplateResponse> createVersion(@AuthenticationPrincipal AuthenticatedUser principal,@PathVariable UUID templateId,@Valid @RequestBody CreateMedicalHistoryTemplateVersionRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.createTemplateVersion(templateId,request,principal.userId()));}
    @PutMapping("/template-versions/{versionId}") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_TEMPLATE_MANAGE')")
    @Operation(summary="Edit an unpublished questionnaire template version")
    public ResponseEntity<MedicalHistoryTemplateResponse> updateVersion(@AuthenticationPrincipal AuthenticatedUser principal,@PathVariable UUID versionId,@Valid @RequestBody CreateMedicalHistoryTemplateVersionRequest request){return ResponseEntity.ok(service.updateTemplateVersion(versionId,request,principal.userId()));}
    @PostMapping("/template-versions/{versionId}/publish") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_TEMPLATE_MANAGE')")
    @Operation(summary="Publish an immutable questionnaire template version")
    public ResponseEntity<MedicalHistoryTemplateResponse> publish(@AuthenticationPrincipal AuthenticatedUser principal,@PathVariable UUID versionId){return ResponseEntity.ok(service.publishTemplate(versionId,principal.userId()));}
    @GetMapping("/template-versions/{versionId}") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_TEMPLATE_READ')")
    @Operation(summary="Get a questionnaire template version") public ResponseEntity<MedicalHistoryTemplateResponse> get(@PathVariable UUID versionId){return ResponseEntity.ok(service.getTemplate(versionId));}
    @GetMapping("/templates/current") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_TEMPLATE_READ')")
    @Operation(summary="Get the currently published questionnaire template") public ResponseEntity<MedicalHistoryTemplateResponse> current(){return ResponseEntity.ok(service.getCurrentTemplate());}
}
