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
import com.dentalcare.api.modules.clinicalrecords.dto.response.ToothStateResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalAttention;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDiagnosis;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalEvolutionNote;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalPreparation;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.FdiToothCatalog;
import com.dentalcare.api.modules.clinicalrecords.model.OdontogramFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;
import com.dentalcare.api.modules.clinicalrecords.model.ToothValidator;
import com.dentalcare.api.modules.medicalhistory.model.MedicalHistory;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return toOdontogramResponse(null, dentition, findings);
    }

    public OdontogramResponse toOdontogramResponse(UUID patientId, DentitionType dentition, List<OdontogramFinding> findings) {
        DentitionType targetDentition = dentition != null ? dentition : DentitionType.ADULT;
        Set<String> standardTeeth = FdiToothCatalog.getStandardTeethForDentition(targetDentition);
        Set<String> allTeethCodes = new LinkedHashSet<>(standardTeeth);

        if (findings != null) {
            for (OdontogramFinding finding : findings) {
                if (finding.getDentition() == targetDentition && FdiToothCatalog.isValidTooth(targetDentition, finding.getToothCode())) {
                    allTeethCodes.add(finding.getToothCode());
                }
            }
        }

        Map<String, ToothBuilder> builders = new LinkedHashMap<>();
        for (String toothCode : allTeethCodes) {
            builders.put(toothCode, new ToothBuilder(toothCode));
        }

        if (findings != null) {
            List<OdontogramFinding> sorted = findings.stream()
                    .filter(f -> f.getDentition() == targetDentition)
                    .sorted(Comparator.comparing(OdontogramFinding::getCreatedAt).thenComparing(OdontogramFinding::getId))
                    .toList();

            for (OdontogramFinding finding : sorted) {
                ToothBuilder builder = builders.get(finding.getToothCode());
                if (builder != null) {
                    builder.apply(finding);
                }
            }
        }

        List<ToothStateResponse> toothStates = new ArrayList<>(builders.size());
        Map<String, ToothFinding> teethSummary = new LinkedHashMap<>(builders.size());

        for (ToothBuilder builder : builders.values()) {
            ToothStateResponse state = builder.build();
            toothStates.add(state);
            teethSummary.put(state.toothCode(), builder.deriveOverallFinding());
        }

        List<OdontogramFindingResponse> recentResponses = findings != null ? findings.stream()
                .filter(f -> f.getDentition() == targetDentition)
                .map(this::toFindingResponse)
                .toList() : List.of();

        return new OdontogramResponse(patientId, targetDentition, toothStates, teethSummary, recentResponses);
    }

    private static class ToothBuilder {
        private final String toothCode;
        private final Integer toothNumber;
        private ToothFinding globalFinding;
        private final Map<ToothSurface, ToothFinding> surfaces;
        private Instant lastUpdatedAt;

        ToothBuilder(String toothCode) {
            this.toothCode = toothCode;
            this.toothNumber = parseToothNumber(toothCode);
            this.globalFinding = null;
            this.surfaces = new LinkedHashMap<>();
            for (ToothSurface surface : FdiToothCatalog.allowedSurfaces(toothCode)) {
                this.surfaces.put(surface, ToothFinding.HEALTHY);
            }
            this.lastUpdatedAt = null;
        }

        private static Integer parseToothNumber(String code) {
            try {
                return Integer.parseInt(code);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        void apply(OdontogramFinding f) {
            this.lastUpdatedAt = f.getCreatedAt();
            if (f.getSurface() == null || f.getFinding().isToothLevelOnly()) {
                if (f.getFinding() == ToothFinding.HEALTHY) {
                    this.globalFinding = null;
                    for (ToothSurface s : this.surfaces.keySet()) {
                        this.surfaces.put(s, ToothFinding.HEALTHY);
                    }
                } else {
                    this.globalFinding = f.getFinding();
                }
            } else {
                ToothSurface surface = f.getSurface();
                if (this.surfaces.containsKey(surface)) {
                    this.surfaces.put(surface, f.getFinding());
                    if (this.globalFinding == ToothFinding.MISSING) {
                        this.globalFinding = null;
                    }
                }
            }
        }

        ToothFinding deriveOverallFinding() {
            if (globalFinding != null) {
                return globalFinding;
            }
            boolean hasCarious = false;
            boolean hasToTreat = false;
            boolean hasFracture = false;
            boolean hasTreated = false;

            for (ToothFinding finding : surfaces.values()) {
                if (finding == ToothFinding.CARIOUS) hasCarious = true;
                else if (finding == ToothFinding.TO_TREAT) hasToTreat = true;
                else if (finding == ToothFinding.FRACTURE) hasFracture = true;
                else if (finding == ToothFinding.TREATED || finding == ToothFinding.RESTORED) hasTreated = true;
            }

            if (hasCarious) return ToothFinding.CARIOUS;
            if (hasToTreat) return ToothFinding.TO_TREAT;
            if (hasFracture) return ToothFinding.FRACTURE;
            if (hasTreated) return ToothFinding.TREATED;
            return ToothFinding.HEALTHY;
        }

        ToothStateResponse build() {
            return new ToothStateResponse(
                    toothCode,
                    toothNumber,
                    globalFinding,
                    Collections.unmodifiableMap(new LinkedHashMap<>(surfaces)),
                    lastUpdatedAt
            );
        }
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
