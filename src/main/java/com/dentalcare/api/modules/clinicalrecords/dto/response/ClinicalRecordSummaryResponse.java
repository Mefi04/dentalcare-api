package com.dentalcare.api.modules.clinicalrecords.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ClinicalRecordSummaryResponse(
        PatientClinicalSummary patient,
        MedicalHistorySummary medicalHistory,
        ClinicalAttentionResponse latestAttention,
        List<ClinicalDiagnosisResponse> recentDiagnoses,
        ClinicalEvolutionResponse latestEvolution,
        OdontogramResponse currentOdontogram,
        List<TreatmentPlanClinicalSummary> treatmentPlans) {

    public record PatientClinicalSummary(
            UUID id,
            String code,
            String fullName,
            LocalDate birthDate) {}

    public record MedicalHistorySummary(
            List<String> allergies,
            List<String> currentMedications,
            List<String> relevantConditions,
            String observations,
            Instant lastUpdated) {}

    public record TreatmentPlanClinicalSummary(
            UUID id,
            String name,
            String status,
            Instant createdAt) {}
}
