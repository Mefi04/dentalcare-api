package com.dentalcare.api.modules.medicalhistory.controller;

import com.dentalcare.api.modules.medicalhistory.dto.request.*;
import com.dentalcare.api.modules.medicalhistory.dto.response.*;
import com.dentalcare.api.modules.medicalhistory.model.*;
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
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/v1")
@Tag(name="Medical history staff workflow",description="Assignment, paper intake, review, validation and immutable history")
public class MedicalHistoryQuestionnaireController {
    private final MedicalHistoryWorkflowService service;
    public MedicalHistoryQuestionnaireController(MedicalHistoryWorkflowService service){this.service=service;}

    @GetMapping("/medical-history/questionnaires") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_STATUS_READ')")
    @Operation(summary="Search questionnaire workflow statuses")
    public ResponseEntity<Page<MedicalHistoryQuestionnaireSummaryResponse>> search(@RequestParam(required=false) UUID patientId,@RequestParam(required=false) QuestionnaireStatus status,@RequestParam(required=false) QuestionnaireSource source,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ResponseEntity.ok(service.search(patientId,status,source,from,to,page,size));}
    @PostMapping("/patients/{patientId}/medical-history/questionnaires") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_ASSIGN')")
    @Operation(summary="Assign the current questionnaire template to a patient")
    public ResponseEntity<MedicalHistoryQuestionnaireResponse> assign(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@Valid @RequestBody AssignMedicalHistoryQuestionnaireRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.assign(patientId,request,p.userId()));}
    @GetMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_CLINICAL_READ')")
    @Operation(summary="Get questionnaire clinical detail") public ResponseEntity<MedicalHistoryQuestionnaireResponse> get(@PathVariable UUID patientId,@PathVariable UUID questionnaireId){return ResponseEntity.ok(service.getForStaff(patientId,questionnaireId));}
    @PatchMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/delivery") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_ASSIGN')")
    @Operation(summary="Record questionnaire delivery") public ResponseEntity<MedicalHistoryQuestionnaireSummaryResponse> deliver(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId){return ResponseEntity.ok(service.markDelivered(patientId,questionnaireId,p.userId()));}
    @PatchMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/receipt") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_RECEIVE')")
    @Operation(summary="Record paper questionnaire receipt and optional scan") public ResponseEntity<MedicalHistoryQuestionnaireSummaryResponse> receive(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId,@RequestBody(required=false) ReceiveMedicalHistoryQuestionnaireRequest request){return ResponseEntity.ok(service.markReceived(patientId,questionnaireId,request,p.userId()));}
    @PutMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/answers") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_TRANSCRIBE')")
    @Operation(summary="Transcribe questionnaire answers") public ResponseEntity<MedicalHistoryQuestionnaireResponse> save(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId,@Valid @RequestBody SaveMedicalHistoryAnswersRequest request){return ResponseEntity.ok(service.saveAnswersForStaff(patientId,questionnaireId,request,p.userId()));}
    @PostMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/submit") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_TRANSCRIBE')")
    @Operation(summary="Submit a transcribed questionnaire snapshot") public ResponseEntity<MedicalHistoryQuestionnaireResponse> submit(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId,@Valid @RequestBody SubmitMedicalHistoryQuestionnaireRequest request){return ResponseEntity.ok(service.submitForStaff(patientId,questionnaireId,request,p.userId()));}
    @PostMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/review") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_REVIEW')")
    @Operation(summary="Start clinical review") public ResponseEntity<MedicalHistoryQuestionnaireResponse> review(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId,@Valid @RequestBody MedicalHistoryVersionedActionRequest request){return ResponseEntity.ok(service.startReview(patientId,questionnaireId,request,p.userId()));}
    @PostMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/notes") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_REVIEW')")
    @Operation(summary="Add an internal clinical review note") public ResponseEntity<MedicalHistoryQuestionnaireResponse> note(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId,@Valid @RequestBody MedicalHistoryReviewNoteRequest request){return ResponseEntity.ok(service.addReviewNote(patientId,questionnaireId,request,p.userId()));}
    @PostMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/clarification") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_REVIEW')")
    @Operation(summary="Request patient clarification") public ResponseEntity<MedicalHistoryQuestionnaireResponse> clarify(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId,@Valid @RequestBody MedicalHistoryTransitionRequest request){return ResponseEntity.ok(service.requestClarification(patientId,questionnaireId,request,p.userId()));}
    @PostMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/validate") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_VALIDATE')")
    @Operation(summary="Validate questionnaire clinically and create an immutable current version") public ResponseEntity<MedicalHistoryQuestionnaireResponse> validate(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId,@Valid @RequestBody MedicalHistoryVersionedActionRequest request){return ResponseEntity.ok(service.validate(patientId,questionnaireId,request,p.userId()));}
    @PostMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/reject") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_VALIDATE')")
    @Operation(summary="Reject a questionnaire without replacing current history") public ResponseEntity<MedicalHistoryQuestionnaireResponse> reject(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId,@Valid @RequestBody MedicalHistoryTransitionRequest request){return ResponseEntity.ok(service.reject(patientId,questionnaireId,request,p.userId()));}
    @PostMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/cancel") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_ASSIGN')")
    @Operation(summary="Cancel a draft or clarification questionnaire") public ResponseEntity<MedicalHistoryQuestionnaireResponse> cancel(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable UUID patientId,@PathVariable UUID questionnaireId,@Valid @RequestBody MedicalHistoryTransitionRequest request){return ResponseEntity.ok(service.cancelForStaff(patientId,questionnaireId,request,p.userId()));}
    @GetMapping("/patients/{patientId}/medical-history/versions") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_CLINICAL_READ')")
    @Operation(summary="List immutable medical history versions") public ResponseEntity<Page<MedicalHistoryVersionResponse>> versions(@PathVariable UUID patientId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ResponseEntity.ok(service.listVersions(patientId,page,size));}
    @GetMapping("/patients/{patientId}/medical-history/versions/{versionId}") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_CLINICAL_READ')")
    @Operation(summary="Get an immutable medical history version") public ResponseEntity<MedicalHistoryVersionResponse> version(@PathVariable UUID patientId,@PathVariable UUID versionId){return ResponseEntity.ok(service.getVersion(patientId,versionId));}
    @GetMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/audit") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_AUDIT_READ')")
    @Operation(summary="Get questionnaire transition audit without answer values") public ResponseEntity<List<MedicalHistoryTransitionEventResponse>> audit(@PathVariable UUID patientId,@PathVariable UUID questionnaireId){return ResponseEntity.ok(service.audit(patientId,questionnaireId));}
    @GetMapping("/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/printable") @PreAuthorize("hasAuthority('MEDICAL_HISTORY_CLINICAL_READ')")
    @Operation(summary="Get a stable questionnaire snapshot suitable for authorized printing") public ResponseEntity<MedicalHistoryQuestionnaireResponse> printable(@PathVariable UUID patientId,@PathVariable UUID questionnaireId){return ResponseEntity.ok(service.getForStaff(patientId,questionnaireId));}
}
