package com.dentalcare.api.modules.clinicalrecords.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CurrentAttentionResponse(
        boolean hasCurrentAttention,
        PatientSummary patient,
        CurrentAttentionDetail currentAttention,
        ClinicalPreparationResponse preparation
) {

    public record PatientSummary(
            UUID id,
            String code,
            String fullName,
            LocalDate birthDate
    ) {}

    public record CurrentAttentionDetail(
            UUID id,
            UUID patientId,
            ClinicalProfessionalResponse professional,
            UUID appointmentId,
            String reason,
            String clinicalNotes,
            String nextSteps,
            Instant occurredAt,
            Instant createdAt,
            Instant updatedAt,
            List<ClinicalDiagnosisResponse> diagnoses,
            List<ClinicalEvolutionResponse> evolutionNotes,
            List<OdontogramFindingResponse> odontogramFindings,
            TreatmentPlanSummary treatmentPlan
    ) {}

    public record TreatmentPlanSummary(
            UUID id,
            String name,
            String status
    ) {}
}
