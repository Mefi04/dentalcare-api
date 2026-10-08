package com.dentalcare.api.modules.medicalhistory.controller;

import com.dentalcare.api.modules.medicalhistory.dto.request.*;
import com.dentalcare.api.modules.medicalhistory.dto.response.*;
import com.dentalcare.api.modules.medicalhistory.service.MedicalHistoryWorkflowService;
import com.dentalcare.api.security.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/patients/me/medical-history")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name="Patient medical history questionnaires",description="Authenticated patient questionnaire self-service without client-provided patient identifiers")
public class PatientMedicalHistoryQuestionnaireController {
    private final MedicalHistoryWorkflowService service;
    public PatientMedicalHistoryQuestionnaireController(MedicalHistoryWorkflowService service){this.service=service;}
    @PostMapping("/change-proposals") @Operation(summary="Create a change proposal against the current validated history")
    public ResponseEntity<MedicalHistoryQuestionnaireResponse> change(@AuthenticationPrincipal AuthenticatedUser p,@Valid @RequestBody CreateMedicalHistoryChangeProposalRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.createChangeProposal(request,p.userId()));}
    @GetMapping("/questionnaires") @Operation(summary="List the authenticated patient's questionnaires")
    public ResponseEntity<Page<MedicalHistoryQuestionnaireSummaryResponse>> list(@AuthenticationPrincipal AuthenticatedUser p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ResponseEntity.ok(service.listMine(p.userId(),page,size));}
    @GetMapping("/questionnaires/{questionnaireId}") @Operation(summary="Get one owned questionnaire with its exact template version")
    public ResponseEntity<MedicalHistoryQuestionnaireResponse> get(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID questionnaireId){return ResponseEntity.ok(service.getMine(p.userId(),questionnaireId));}
    @PutMapping("/questionnaires/{questionnaireId}/answers") @Operation(summary="Save an owned questionnaire draft")
    public ResponseEntity<MedicalHistoryQuestionnaireResponse> save(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID questionnaireId,@Valid @RequestBody SaveMedicalHistoryAnswersRequest request){return ResponseEntity.ok(service.saveMyAnswers(p.userId(),questionnaireId,request));}
    @PostMapping("/questionnaires/{questionnaireId}/submit") @Operation(summary="Submit an immutable answer snapshot with patient attestation")
    public ResponseEntity<MedicalHistoryQuestionnaireResponse> submit(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID questionnaireId,@Valid @RequestBody SubmitMedicalHistoryQuestionnaireRequest request){return ResponseEntity.ok(service.submitMine(p.userId(),questionnaireId,request));}
    @PostMapping("/questionnaires/{questionnaireId}/cancel") @Operation(summary="Cancel an owned draft or clarification questionnaire")
    public ResponseEntity<MedicalHistoryQuestionnaireResponse> cancel(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID questionnaireId,@Valid @RequestBody MedicalHistoryTransitionRequest request){return ResponseEntity.ok(service.cancelMine(p.userId(),questionnaireId,request));}
    @GetMapping("/versions/current") @Operation(summary="Get the authenticated patient's current validated medical history version")
    public ResponseEntity<MedicalHistoryVersionResponse> current(@AuthenticationPrincipal AuthenticatedUser p){return ResponseEntity.ok(service.getMyCurrentVersion(p.userId()));}
}
