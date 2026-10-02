package com.dentalcare.api.modules.clinicalrecords.mapper;

import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDiagnosisResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalEvolutionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalRecordSummaryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramFindingResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ToothStateResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalAttention;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDiagnosis;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalEvolutionNote;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import com.dentalcare.api.modules.clinicalrecords.model.OdontogramFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;
import com.dentalcare.api.modules.medicalhistory.model.MedicalHistory;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalRecordMapperTests {

    private ClinicalRecordMapper mapper;
    private Patient patient;
    private User professional;

    @BeforeEach
    void setUp() {
        mapper = new ClinicalRecordMapper();
        patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode("EXP-2026-001");
        patient.setName("Mariana Lopez");
        patient.setDpi("1234567890101");
        patient.setBirthDate(LocalDate.of(1995, 5, 20));
        patient.setGender(Gender.FEMALE);
        patient.setPhone("555-0101");
        patient.setEmail("mariana@example.com");
        patient.setCreatedAt(Instant.now());
        patient.setUpdatedAt(Instant.now());

        professional = new User(
                UUID.randomUUID(), "dentist.user", "Dra. Valeria Soto", "valeria@example.com",
                "1234567890102", "$2a$10$hashedpassword", UserStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
    }

    @Test
    void mapsAttentionToResponseCorrectly() {
        Instant now = Instant.now();
        ClinicalAttention attention = new ClinicalAttention(
                UUID.randomUUID(), patient, professional, null,
                "Routine checkup", "Normal conditions", "Follow up in 6 months",
                now, now, now
        );

        ClinicalAttentionResponse response = mapper.toAttentionResponse(attention);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(attention.getId());
        assertThat(response.patientId()).isEqualTo(patient.getId());
        assertThat(response.professional().id()).isEqualTo(professional.getId());
        assertThat(response.professional().fullName()).isEqualTo("Dra. Valeria Soto");
        assertThat(response.appointmentId()).isNull();
        assertThat(response.reason()).isEqualTo("Routine checkup");
        assertThat(response.clinicalNotes()).isEqualTo("Normal conditions");
        assertThat(response.nextSteps()).isEqualTo("Follow up in 6 months");
    }

    @Test
    void mapsDiagnosisToResponseCorrectly() {
        Instant now = Instant.now();
        ClinicalAttention attention = new ClinicalAttention(
                UUID.randomUUID(), patient, professional, null,
                "Consultation", "Notes", null, now, now, now
        );
        ClinicalDiagnosis diagnosis = new ClinicalDiagnosis(
                UUID.randomUUID(), patient, attention, null, professional,
                DiagnosisType.PRIMARY, "Caries profunda en pieza 16", now
        );

        ClinicalDiagnosisResponse response = mapper.toDiagnosisResponse(diagnosis);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(diagnosis.getId());
        assertThat(response.patientId()).isEqualTo(patient.getId());
        assertThat(response.attentionId()).isEqualTo(attention.getId());
        assertThat(response.treatmentPlanId()).isNull();
        assertThat(response.type()).isEqualTo(DiagnosisType.PRIMARY);
        assertThat(response.description()).isEqualTo("Caries profunda en pieza 16");
        assertThat(response.author().fullName()).isEqualTo("Dra. Valeria Soto");
    }

    @Test
    void mapsEvolutionNoteToResponseCorrectly() {
        Instant now = Instant.now();
        ClinicalAttention attention = new ClinicalAttention(
                UUID.randomUUID(), patient, professional, null,
                "Consultation", "Notes", null, now, now, now
        );
        LocalDate date = LocalDate.of(2026, 9, 15);
        ClinicalEvolutionNote note = new ClinicalEvolutionNote(
                UUID.randomUUID(), patient, attention, professional,
                date, "Limpieza y profilaxis", "Paciente refiere alivio.", now
        );

        ClinicalEvolutionResponse response = mapper.toEvolutionResponse(note);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(note.getId());
        assertThat(response.consultationDate()).isEqualTo(date);
        assertThat(response.procedureSummary()).isEqualTo("Limpieza y profilaxis");
        assertThat(response.note()).isEqualTo("Paciente refiere alivio.");
        assertThat(response.author().fullName()).isEqualTo("Dra. Valeria Soto");
    }

    @Test
    void mapsOdontogramFindingToResponseCorrectly() {
        Instant now = Instant.now();
        OdontogramFinding finding = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "16", ToothSurface.OCCLUSAL,
                ToothFinding.CARIOUS, "Caries oclusal incipiente", now
        );

        OdontogramFindingResponse response = mapper.toFindingResponse(finding);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(finding.getId());
        assertThat(response.dentition()).isEqualTo(DentitionType.ADULT);
        assertThat(response.toothCode()).isEqualTo("16");
        assertThat(response.surface()).isEqualTo(ToothSurface.OCCLUSAL);
        assertThat(response.finding()).isEqualTo(ToothFinding.CARIOUS);
        assertThat(response.observation()).isEqualTo("Caries oclusal incipiente");
    }

    @Test
    void buildsOdontogramResponseWithStandardTeethAndOverlaidFindings() {
        Instant now = Instant.now();
        OdontogramFinding f1 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "16", ToothSurface.OCCLUSAL,
                ToothFinding.CARIOUS, null, now.minusSeconds(100)
        );
        OdontogramFinding f2 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "16", ToothSurface.OCCLUSAL,
                ToothFinding.TREATED, "Restaurado con resina", now
        );
        OdontogramFinding f3 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "24", ToothSurface.VESTIBULAR,
                ToothFinding.MISSING, null, now
        );

        OdontogramResponse response = mapper.toOdontogramResponse(DentitionType.ADULT, List.of(f1, f2, f3));

        assertThat(response.dentition()).isEqualTo(DentitionType.ADULT);
        assertThat(response.teeth()).hasSize(32);
        // Piece 16 should be updated to TREATED by latest finding f2
        assertThat(response.teethSummary().get("16")).isEqualTo(ToothFinding.TREATED);
        assertThat(response.teethSummary().get("24")).isEqualTo(ToothFinding.MISSING);
        // Untouched piece should default to HEALTHY
        assertThat(response.teethSummary().get("11")).isEqualTo(ToothFinding.HEALTHY);
        assertThat(response.teethSummary().get("48")).isEqualTo(ToothFinding.HEALTHY);

        ToothStateResponse tooth16 = response.teeth().stream()
                .filter(t -> t.toothCode().equals("16"))
                .findFirst().orElseThrow();
        assertThat(tooth16.surfaces().get(ToothSurface.OCCLUSAL)).isEqualTo(ToothFinding.TREATED);
        assertThat(tooth16.surfaces().get(ToothSurface.MESIAL)).isEqualTo(ToothFinding.HEALTHY);
    }

    @Test
    void chronologicalResolutionOfSurfaceFindingsUpdatesStateAccurately() {
        Instant now = Instant.now();
        OdontogramFinding f1 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "16", ToothSurface.OCCLUSAL,
                ToothFinding.CARIOUS, "Caries detectada", now.minusSeconds(100)
        );
        OdontogramFinding f2 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "16", ToothSurface.OCCLUSAL,
                ToothFinding.RESTORED, "Resina oclusal", now.minusSeconds(50)
        );
        OdontogramFinding f3 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "16", ToothSurface.MESIAL,
                ToothFinding.CARIOUS, "Nueva caries mesial", now
        );

        OdontogramResponse response = mapper.toOdontogramResponse(patient.getId(), DentitionType.ADULT, List.of(f1, f2, f3));

        ToothStateResponse tooth16 = response.teeth().stream()
                .filter(t -> t.toothCode().equals("16"))
                .findFirst().orElseThrow();

        assertThat(tooth16.surfaces().get(ToothSurface.OCCLUSAL)).isEqualTo(ToothFinding.RESTORED);
        assertThat(tooth16.surfaces().get(ToothSurface.MESIAL)).isEqualTo(ToothFinding.CARIOUS);
        assertThat(tooth16.surfaces().get(ToothSurface.DISTAL)).isEqualTo(ToothFinding.HEALTHY);
        assertThat(tooth16.globalFinding()).isNull();
        // Since MESIAL has active caries, overall piece summary is CARIOUS
        assertThat(response.teethSummary().get("16")).isEqualTo(ToothFinding.CARIOUS);
    }

    @Test
    void globalMissingHasVisualPriorityOverPriorSurfacesWithoutErasingHistory() {
        Instant now = Instant.now();
        OdontogramFinding f1 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "21", ToothSurface.INCISAL,
                ToothFinding.CARIOUS, "Caries previa", now.minusSeconds(200)
        );
        OdontogramFinding f2 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "21", null,
                ToothFinding.MISSING, "Pieza perdida por avulsion", now.minusSeconds(100)
        );

        OdontogramResponse response = mapper.toOdontogramResponse(patient.getId(), DentitionType.ADULT, List.of(f1, f2));

        ToothStateResponse tooth21 = response.teeth().stream()
                .filter(t -> t.toothCode().equals("21"))
                .findFirst().orElseThrow();

        assertThat(tooth21.globalFinding()).isEqualTo(ToothFinding.MISSING);
        // Surface history retained in the surface map
        assertThat(tooth21.surfaces().get(ToothSurface.INCISAL)).isEqualTo(ToothFinding.CARIOUS);
        // Overall summary reflects MISSING
        assertThat(response.teethSummary().get("21")).isEqualTo(ToothFinding.MISSING);
    }

    @Test
    void recoveringToothFromMissingUpdatesGlobalStateViaSubsequentEvent() {
        Instant now = Instant.now();
        OdontogramFinding f1 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "36", null,
                ToothFinding.MISSING, "Exodoncia previa", now.minusSeconds(200)
        );
        OdontogramFinding f2 = new OdontogramFinding(
                UUID.randomUUID(), patient, null, professional,
                DentitionType.ADULT, "36", null,
                ToothFinding.IMPLANT, "Implante oseointegrado", now.minusSeconds(100)
        );

        OdontogramResponse response = mapper.toOdontogramResponse(patient.getId(), DentitionType.ADULT, List.of(f1, f2));

        ToothStateResponse tooth36 = response.teeth().stream()
                .filter(t -> t.toothCode().equals("36"))
                .findFirst().orElseThrow();

        assertThat(tooth36.globalFinding()).isEqualTo(ToothFinding.IMPLANT);
        assertThat(response.teethSummary().get("36")).isEqualTo(ToothFinding.IMPLANT);
    }

    @Test
    void anatomicallyDistinguishesAnteriorMaxillaryAndPosteriorMandibularSurfaces() {
        OdontogramResponse response = mapper.toOdontogramResponse(DentitionType.ADULT, List.of());

        // Tooth 11: Anterior Maxillary (Incisal, Palatal, Mesial, Distal, Vestibular)
        ToothStateResponse tooth11 = response.teeth().stream()
                .filter(t -> t.toothCode().equals("11"))
                .findFirst().orElseThrow();
        assertThat(tooth11.surfaces().keySet()).containsExactlyInAnyOrder(
                ToothSurface.MESIAL, ToothSurface.DISTAL, ToothSurface.VESTIBULAR,
                ToothSurface.PALATAL, ToothSurface.INCISAL
        );

        // Tooth 46: Posterior Mandibular (Occlusal, Lingual, Mesial, Distal, Vestibular)
        ToothStateResponse tooth46 = response.teeth().stream()
                .filter(t -> t.toothCode().equals("46"))
                .findFirst().orElseThrow();
        assertThat(tooth46.surfaces().keySet()).containsExactlyInAnyOrder(
                ToothSurface.MESIAL, ToothSurface.DISTAL, ToothSurface.VESTIBULAR,
                ToothSurface.LINGUAL, ToothSurface.OCCLUSAL
        );
    }

    @Test
    void buildsSummaryResponseConsolidatingDomains() {
        Instant now = Instant.now();
        MedicalHistory medicalHistory = new MedicalHistory(
                UUID.randomUUID(), patient, now, now
        );
        medicalHistory.replaceAllergies(Set.of("Penicilina"));
        medicalHistory.replaceCurrentMedications(Set.of("Ibuprofeno"));
        medicalHistory.replaceRelevantConditions(Set.of("Hipertension"));
        medicalHistory.setObservations("Observaciones generales");

        ClinicalAttention attention = new ClinicalAttention(
                UUID.randomUUID(), patient, professional, null,
                "Consulta inicial", "Notas", "Seguimiento", now, now, now
        );
        TreatmentPlan plan = new TreatmentPlan(
                UUID.randomUUID(), patient, professional,
                "Plan Ortodoncia", "Fase 1", TreatmentPlanStatus.DRAFT,
                now, now
        );

        OdontogramResponse odontogram = mapper.toOdontogramResponse(DentitionType.ADULT, List.of());

        ClinicalRecordSummaryResponse summary = mapper.toSummaryResponse(
                patient, medicalHistory, attention, List.of(), null, odontogram, List.of(plan)
        );

        assertThat(summary.patient().id()).isEqualTo(patient.getId());
        assertThat(summary.patient().fullName()).isEqualTo("Mariana Lopez");
        assertThat(summary.medicalHistory().allergies()).containsExactly("Penicilina");
        assertThat(summary.medicalHistory().currentMedications()).containsExactly("Ibuprofeno");
        assertThat(summary.latestAttention().reason()).isEqualTo("Consulta inicial");
        assertThat(summary.treatmentPlans()).hasSize(1);
        assertThat(summary.treatmentPlans().getFirst().name()).isEqualTo("Plan Ortodoncia");
    }
}
