package com.dentalcare.api.modules.clinicalrecords.mapper;

import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDiagnosisResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalEvolutionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalHistoryEntryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalPreparationResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalProfessionalResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalRecordSummaryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramFindingResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalAttention;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDiagnosis;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalEvolutionNote;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalPreparation;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.OdontogramFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothValidator;
import com.dentalcare.api.modules.medicalhistory.model.MedicalHistory;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ClinicalRecordMapper {

    public ClinicalProfessionalResponse toProfessionalResponse(User user) {
        if (user == null) {
            return null;
        }
        return new ClinicalProfessionalResponse(user.getId(), user.getFullName());
    }

    public ClinicalAttentionResponse toAttentionResponse(ClinicalAttention attention) {
        if (attention == null) {
            return null;
        }
        return new ClinicalAttentionResponse(
                attention.getId(),
                attention.getPatient().getId(),
                toProfessionalResponse(attention.getProfessional()),
                attention.getAppointment() != null ? attention.getAppointment().getId() : null,
                attention.getReason(),
                attention.getClinicalNotes(),
                attention.getNextSteps(),
                attention.getOccurredAt(),
                attention.getCreatedAt(),
                attention.getUpdatedAt()
        );
    }

    public ClinicalDiagnosisResponse toDiagnosisResponse(ClinicalDiagnosis diagnosis) {
        if (diagnosis == null) {
            return null;
        }
        return new ClinicalDiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getPatient().getId(),
                diagnosis.getAttention().getId(),
                diagnosis.getTreatmentPlan() != null ? diagnosis.getTreatmentPlan().getId() : null,
                toProfessionalResponse(diagnosis.getAuthor()),
                diagnosis.getType(),
                diagnosis.getDescription(),
                diagnosis.getCreatedAt()
        );
    }

    public ClinicalEvolutionResponse toEvolutionResponse(ClinicalEvolutionNote note) {
        if (note == null) {
            return null;
        }
        return new ClinicalEvolutionResponse(
                note.getId(),
                note.getPatient().getId(),
                note.getAttention().getId(),
                toProfessionalResponse(note.getAuthor()),
                note.getConsultationDate(),
                note.getProcedureSummary(),
                note.getNote(),
                note.getCreatedAt()
        );
    }

    public OdontogramFindingResponse toFindingResponse(OdontogramFinding finding) {
        if (finding == null) {
            return null;
        }
        return new OdontogramFindingResponse(
                finding.getId(),
                finding.getPatient().getId(),
                finding.getAttention() != null ? finding.getAttention().getId() : null,
                toProfessionalResponse(finding.getAuthor()),
                finding.getDentition(),
                finding.getToothCode(),
                finding.getSurface(),
                finding.getFinding(),
                finding.getObservation(),
                finding.getCreatedAt()
        );
    }

    public OdontogramResponse toOdontogramResponse(DentitionType dentition, List<OdontogramFinding> findings) {
        Map<String, ToothFinding> teethMap = new LinkedHashMap<>();
        for (String tooth : ToothValidator.getStandardTeethForDentition(dentition)) {
            teethMap.put(tooth, ToothFinding.HEALTHY);
        }

        // Overlay with findings in chronological order so later findings overwrite earlier ones
        if (findings != null) {
            for (OdontogramFinding finding : findings) {
                if (finding.getDentition() == dentition && teethMap.containsKey(finding.getToothCode())) {
                    teethMap.put(finding.getToothCode(), finding.getFinding());
                }
            }
        }

        List<OdontogramFindingResponse> recentResponses = findings != null ? findings.stream()
                .filter(f -> f.getDentition() == dentition)
                .map(this::toFindingResponse)
                .toList() : List.of();

        return new OdontogramResponse(dentition, teethMap, recentResponses);
    }

    public ClinicalPreparationResponse toPreparationResponse(ClinicalPreparation prep, MedicalHistory history) {
        if (prep == null) {
            return null;
        }
        List<String> allergies = (history != null && history.getAllergies() != null)
                ? List.copyOf(history.getAllergies())
                : List.of();
        List<String> medications = (history != null && history.getCurrentMedications() != null)
                ? List.copyOf(history.getCurrentMedications())
                : List.of();

        return new ClinicalPreparationResponse(
                prep.getId(),
                prep.getPatient().getId(),
                prep.getAttention() != null ? prep.getAttention().getId() : null,
                toProfessionalResponse(prep.getPreparedBy()),
                prep.getBloodPressure(),
                prep.getHeartRate(),
                prep.getTemperature(),
                prep.getWeight(),
                prep.getObservations(),
                allergies,
                medications,
                prep.getCreatedAt(),
                prep.getUpdatedAt()
        );
    }

    public ClinicalHistoryEntryResponse fromAttention(ClinicalAttention attention) {
        return new ClinicalHistoryEntryResponse(
                attention.getId(),
                "Atención registrada: " + attention.getReason(),
                "ATTENTION",
                attention.getClinicalNotes(),
                attention.getProfessional().getFullName(),
                attention.getOccurredAt()
        );
    }

    public ClinicalHistoryEntryResponse fromPreparation(ClinicalPreparation prep) {
        List<String> details = new ArrayList<>();
        if (prep.getBloodPressure() != null) details.add("P/A: " + prep.getBloodPressure());
        if (prep.getHeartRate() != null) details.add("FC: " + prep.getHeartRate() + " lpm");
        if (prep.getTemperature() != null) details.add("Temp: " + prep.getTemperature() + " °C");
        if (prep.getWeight() != null) details.add("Peso: " + prep.getWeight() + " kg");
        if (prep.getObservations() != null && !prep.getObservations().isBlank()) {
            details.add(prep.getObservations());
        }
        String desc = details.isEmpty() ? "Preparación clínica completada" : String.join(" | ", details);
        return new ClinicalHistoryEntryResponse(
                prep.getId(),
                "Preparación pre-atención",
                "PREPARATION",
                desc,
                prep.getPreparedBy().getFullName(),
                prep.getCreatedAt()
        );
    }

    public ClinicalHistoryEntryResponse fromDocument(ClinicalDocument document) {
        String description = document.getDescription() != null && !document.getDescription().isBlank()
                ? document.getDescription()
                : "Tipo: " + document.getType().name();
        String authorName = document.getAuthor() != null ? document.getAuthor().getFullName() : "Sistema";
        return new ClinicalHistoryEntryResponse(
                document.getId(),
                "Documento adjunto: " + document.getTitle(),
                "DOCUMENT",
                description,
                authorName,
                document.getCreatedAt()
        );
    }

    public ClinicalHistoryEntryResponse fromDiagnosis(ClinicalDiagnosis diagnosis) {
        return new ClinicalHistoryEntryResponse(
                diagnosis.getId(),
                "Diagnóstico " + (diagnosis.getType() != null ? diagnosis.getType().name() : "") + ": " + diagnosis.getDescription(),
                "DIAGNOSIS",
                diagnosis.getDescription(),
                diagnosis.getAuthor().getFullName(),
                diagnosis.getCreatedAt()
        );
    }

    public ClinicalHistoryEntryResponse fromEvolution(ClinicalEvolutionNote note) {
        return new ClinicalHistoryEntryResponse(
                note.getId(),
                "Evolución clínica: " + note.getProcedureSummary(),
                "EVOLUTION",
                note.getNote(),
                note.getAuthor().getFullName(),
                note.getCreatedAt()
        );
    }

    public ClinicalHistoryEntryResponse fromFinding(OdontogramFinding finding) {
        String detail = "Pieza " + finding.getToothCode() + " - " + finding.getFinding().name()
                + (finding.getSurface() != null ? " (" + finding.getSurface().name() + ")" : "");
        return new ClinicalHistoryEntryResponse(
                finding.getId(),
                "Hallazgo odontograma: " + detail,
                "ODONTOGRAM",
                finding.getObservation() != null ? finding.getObservation() : detail,
                finding.getAuthor().getFullName(),
                finding.getCreatedAt()
        );
    }

    public List<ClinicalHistoryEntryResponse> fromTreatmentProcedure(TreatmentProcedure procedure) {
        List<ClinicalHistoryEntryResponse> entries = new ArrayList<>();
        entries.add(new ClinicalHistoryEntryResponse(
                eventId(procedure.getId(), "STARTED"),
                "Procedimiento iniciado: " + procedure.getProcedureName(),
                "TREATMENT_PROCEDURE_STARTED",
                procedureDetail(procedure.getTooth(), procedure.getClinicalObservations(), null),
                procedure.getProfessional().getFullName(),
                procedure.getPerformedAt()));

        if (procedure.getStatus() == TreatmentProcedureStatus.COMPLETED
                && procedure.getCompletedAt() != null) {
            entries.add(new ClinicalHistoryEntryResponse(
                    eventId(procedure.getId(), "COMPLETED"),
                    "Procedimiento completado: " + procedure.getProcedureName(),
                    "TREATMENT_PROCEDURE_COMPLETED",
                    procedureDetail(procedure.getTooth(), procedure.getClinicalObservations(),
                            procedure.getCompletionNotes()),
                    procedure.getProfessional().getFullName(),
                    procedure.getCompletedAt()));
        }
        return List.copyOf(entries);
    }

    private UUID eventId(UUID procedureId, String event) {
        return UUID.nameUUIDFromBytes(("treatment-procedure:" + procedureId + ":" + event)
                .getBytes(StandardCharsets.UTF_8));
    }

    private String procedureDetail(String tooth, String observations, String completionNotes) {
        List<String> details = new ArrayList<>();
        if (tooth != null && !tooth.isBlank()) details.add("Pieza " + tooth);
        if (observations != null && !observations.isBlank()) details.add("Observaciones: " + observations);
        if (completionNotes != null && !completionNotes.isBlank()) {
            details.add("Notas de finalización: " + completionNotes);
        }
        return details.isEmpty() ? "Sin observaciones clínicas" : String.join(". ", details);
    }

    public ClinicalRecordSummaryResponse toSummaryResponse(
            Patient patient,
            MedicalHistory medicalHistory,
            ClinicalAttention latestAttention,
            List<ClinicalDiagnosis> recentDiagnoses,
            ClinicalEvolutionNote latestEvolution,
            OdontogramResponse currentOdontogram,
            List<TreatmentPlan> treatmentPlans) {

        ClinicalRecordSummaryResponse.PatientClinicalSummary patientSummary =
                new ClinicalRecordSummaryResponse.PatientClinicalSummary(
                        patient.getId(),
                        patient.getCode(),
                        patient.getName(),
                        patient.getBirthDate()
                );

        ClinicalRecordSummaryResponse.MedicalHistorySummary historySummary = medicalHistory != null
                ? new ClinicalRecordSummaryResponse.MedicalHistorySummary(
                        medicalHistory.getAllergies() != null ? List.copyOf(medicalHistory.getAllergies()) : List.of(),
                        medicalHistory.getCurrentMedications() != null ? List.copyOf(medicalHistory.getCurrentMedications()) : List.of(),
                        medicalHistory.getRelevantConditions() != null ? List.copyOf(medicalHistory.getRelevantConditions()) : List.of(),
                        medicalHistory.getObservations(),
                        medicalHistory.getUpdatedAt()
                )
                : new ClinicalRecordSummaryResponse.MedicalHistorySummary(
                        List.of(), List.of(), List.of(), null, null
                );

        List<ClinicalDiagnosisResponse> diagnosesResponses = recentDiagnoses != null
                ? recentDiagnoses.stream().map(this::toDiagnosisResponse).toList()
                : List.of();

        List<ClinicalRecordSummaryResponse.TreatmentPlanClinicalSummary> planSummaries = treatmentPlans != null
                ? treatmentPlans.stream().map(p -> new ClinicalRecordSummaryResponse.TreatmentPlanClinicalSummary(
                        p.getId(), p.getName(), p.getStatus().name(), p.getCreatedAt())).toList()
                : List.of();

        return new ClinicalRecordSummaryResponse(
                patientSummary,
                historySummary,
                toAttentionResponse(latestAttention),
                diagnosesResponses,
                toEvolutionResponse(latestEvolution),
                currentOdontogram,
                planSummaries
        );
    }
}
