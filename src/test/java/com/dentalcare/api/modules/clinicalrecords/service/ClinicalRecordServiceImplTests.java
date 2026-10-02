package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalAttentionRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDiagnosisRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateEvolutionNoteRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateOdontogramFindingRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDiagnosisResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalPreparationRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalEvolutionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalHistoryEntryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalPreparationResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalRecordSummaryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.CurrentAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramFindingResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramResponse;
import com.dentalcare.api.modules.clinicalrecords.mapper.ClinicalRecordMapper;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalAttention;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDiagnosis;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalEvolutionNote;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalPreparation;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import com.dentalcare.api.modules.clinicalrecords.model.OdontogramFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalAttentionRepository;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDiagnosisRepository;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDocumentRepository;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalEvolutionNoteRepository;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalPreparationRepository;
import com.dentalcare.api.modules.clinicalrecords.repository.OdontogramFindingRepository;
import com.dentalcare.api.modules.medicalhistory.model.MedicalHistory;
import com.dentalcare.api.modules.medicalhistory.repository.MedicalHistoryRepository;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentProcedureRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalRecordServiceImplTests {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Mock private ClinicalAttentionRepository clinicalAttentionRepository;
    @Mock private ClinicalDiagnosisRepository clinicalDiagnosisRepository;
    @Mock private ClinicalEvolutionNoteRepository clinicalEvolutionNoteRepository;
    @Mock private OdontogramFindingRepository odontogramFindingRepository;
    @Mock private ClinicalDocumentRepository clinicalDocumentRepository;
    @Mock private ClinicalPreparationRepository clinicalPreparationRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private TreatmentPlanRepository treatmentPlanRepository;
    @Mock private TreatmentProcedureRepository treatmentProcedureRepository;
    @Mock private MedicalHistoryRepository medicalHistoryRepository;

    private ClinicalRecordService clinicalRecordService;
    private Patient patientA;
    private Patient patientB;
    private User dentist;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        ClinicalRecordMapper mapper = new ClinicalRecordMapper();
        clinicalRecordService = new ClinicalRecordServiceImpl(
                clinicalAttentionRepository, clinicalDiagnosisRepository,
                clinicalEvolutionNoteRepository, odontogramFindingRepository,
                clinicalDocumentRepository, clinicalPreparationRepository,
                patientRepository, userRepository, appointmentRepository,
                treatmentPlanRepository, treatmentProcedureRepository,
                medicalHistoryRepository, mapper, fixedClock
        );

        patientA = createPatient("EXP-001", "Paciente A", "1111111111111");
        patientB = createPatient("EXP-002", "Paciente B", "2222222222222");

        dentist = new User(
                UUID.randomUUID(), "dentist1", "Dr. Clinico", "dentist@test.com",
                "3333333333333", "hash", UserStatus.ACTIVE,
                FIXED_NOW, FIXED_NOW
        );
    }

    private Patient createPatient(String code, String name, String dpi) {
        Patient p = new Patient();
        p.setId(UUID.randomUUID());
        p.setCode(code);
        p.setName(name);
        p.setDpi(dpi);
        p.setBirthDate(LocalDate.of(1990, 1, 1));
        p.setGender(Gender.OTHER);
        p.setPhone("5555-0000");
        p.setCreatedAt(FIXED_NOW);
        p.setUpdatedAt(FIXED_NOW);
        return p;
    }

    @Test
    void createAttentionSucceedsForValidPatientAndActiveDentist() {
        CreateClinicalAttentionRequest request = new CreateClinicalAttentionRequest(
                "Dolor agudo", "Caries visible en molar", "Realizar profilaxis y restaurar", null
        );
        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(userRepository.findById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(clinicalAttentionRepository.save(any(ClinicalAttention.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ClinicalAttentionResponse response = clinicalRecordService.createAttention(
                patientA.getId(), request, dentist.getId()
        );

        assertThat(response).isNotNull();
        assertThat(response.patientId()).isEqualTo(patientA.getId());
        assertThat(response.professional().id()).isEqualTo(dentist.getId());
        assertThat(response.reason()).isEqualTo("Dolor agudo");
        assertThat(response.clinicalNotes()).isEqualTo("Caries visible en molar");
        assertThat(response.nextSteps()).isEqualTo("Realizar profilaxis y restaurar");
        verify(clinicalAttentionRepository).save(any(ClinicalAttention.class));
    }

    @Test
    void createAttentionRejectsAppointmentBelongingToAnotherPatient_AntiIdor() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointmentOfB = new Appointment(
                appointmentId, patientB, dentist, FIXED_NOW, AppointmentStatus.SCHEDULED, FIXED_NOW, FIXED_NOW
        );
        CreateClinicalAttentionRequest request = new CreateClinicalAttentionRequest(
                "Consulta", "Notas", null, appointmentId
        );

        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(userRepository.findById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointmentOfB));

        assertThatThrownBy(() -> clinicalRecordService.createAttention(patientA.getId(), request, dentist.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Appointment does not belong to the patient");
    }

    @Test
    void createAttentionThrowsWhenPatientNotFound() {
        CreateClinicalAttentionRequest request = new CreateClinicalAttentionRequest(
                "Motivo", "Notas", null, null
        );
        UUID randomPatientId = UUID.randomUUID();
        when(patientRepository.findById(randomPatientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clinicalRecordService.createAttention(randomPatientId, request, dentist.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Patient not found");
    }

    @Test
    void createAttentionThrowsWhenUserInactive() {
        User inactiveUser = new User(
                UUID.randomUUID(), "inactive", "Inactive", "in@test.com",
                "4444444444444", "hash", UserStatus.INACTIVE,
                FIXED_NOW, FIXED_NOW
        );
        CreateClinicalAttentionRequest request = new CreateClinicalAttentionRequest(
                "Motivo", "Notas", null, null
        );
        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(userRepository.findById(inactiveUser.getId())).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> clinicalRecordService.createAttention(patientA.getId(), request, inactiveUser.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authenticated user is not active");
    }

    @Test
    void createDiagnosisRejectsTreatmentPlanBelongingToAnotherPatient_AntiIdor() {
        UUID attentionId = UUID.randomUUID();
        ClinicalAttention attentionA = new ClinicalAttention(
                attentionId, patientA, dentist, null, "Consulta", "Notas", null,
                FIXED_NOW, FIXED_NOW, FIXED_NOW
        );
        UUID planId = UUID.randomUUID();
        TreatmentPlan planB = new TreatmentPlan(
                planId, patientB, dentist, "Plan de B", null, TreatmentPlanStatus.DRAFT,
                FIXED_NOW, FIXED_NOW
        );
        CreateClinicalDiagnosisRequest request = new CreateClinicalDiagnosisRequest(
                DiagnosisType.PRIMARY, "Gingivitis marginal", planId
        );

        when(clinicalAttentionRepository.findById(attentionId)).thenReturn(Optional.of(attentionA));
        when(userRepository.findById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(treatmentPlanRepository.findById(planId)).thenReturn(Optional.of(planB));

        assertThatThrownBy(() -> clinicalRecordService.createDiagnosis(attentionId, request, dentist.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Treatment plan does not belong to the patient");
    }

    @Test
    void createDiagnosisSucceedsWhenValid() {
        UUID attentionId = UUID.randomUUID();
        ClinicalAttention attentionA = new ClinicalAttention(
                attentionId, patientA, dentist, null, "Consulta", "Notas", null,
                FIXED_NOW, FIXED_NOW, FIXED_NOW
        );
        CreateClinicalDiagnosisRequest request = new CreateClinicalDiagnosisRequest(
                DiagnosisType.PRIMARY, "Caries en esmalte", null
        );

        when(clinicalAttentionRepository.findById(attentionId)).thenReturn(Optional.of(attentionA));
        when(userRepository.findById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(clinicalDiagnosisRepository.save(any(ClinicalDiagnosis.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ClinicalDiagnosisResponse response = clinicalRecordService.createDiagnosis(
                attentionId, request, dentist.getId()
        );

        assertThat(response).isNotNull();
        assertThat(response.patientId()).isEqualTo(patientA.getId());
        assertThat(response.attentionId()).isEqualTo(attentionId);
        assertThat(response.type()).isEqualTo(DiagnosisType.PRIMARY);
        assertThat(response.description()).isEqualTo("Caries en esmalte");
    }

    @Test
    void createEvolutionNoteSucceedsWhenValid() {
        UUID attentionId = UUID.randomUUID();
        ClinicalAttention attentionA = new ClinicalAttention(
                attentionId, patientA, dentist, null, "Consulta", "Notas", null,
                FIXED_NOW, FIXED_NOW, FIXED_NOW
        );
        CreateEvolutionNoteRequest request = new CreateEvolutionNoteRequest(
                LocalDate.of(2026, 9, 20), "Obturacion con resina", "Paciente asintomatico al finalizar"
        );

        when(clinicalAttentionRepository.findById(attentionId)).thenReturn(Optional.of(attentionA));
        when(userRepository.findById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(clinicalEvolutionNoteRepository.save(any(ClinicalEvolutionNote.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ClinicalEvolutionResponse response = clinicalRecordService.createEvolutionNote(
                attentionId, request, dentist.getId()
        );

        assertThat(response).isNotNull();
        assertThat(response.patientId()).isEqualTo(patientA.getId());
        assertThat(response.procedureSummary()).isEqualTo("Obturacion con resina");
        assertThat(response.note()).isEqualTo("Paciente asintomatico al finalizar");
    }

    @Test
    void createOdontogramFindingRejectsAttentionOfAnotherPatient_AntiIdor() {
        UUID attentionId = UUID.randomUUID();
        ClinicalAttention attentionB = new ClinicalAttention(
                attentionId, patientB, dentist, null, "Consulta", "Notas", null,
                FIXED_NOW, FIXED_NOW, FIXED_NOW
        );
        CreateOdontogramFindingRequest request = new CreateOdontogramFindingRequest(
                attentionId, DentitionType.ADULT, "16", ToothSurface.OCCLUSAL,
                ToothFinding.CARIOUS, "Caries profunda"
        );

        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(userRepository.findById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(clinicalAttentionRepository.findById(attentionId)).thenReturn(Optional.of(attentionB));

        assertThatThrownBy(() -> clinicalRecordService.createOdontogramFinding(patientA.getId(), request, dentist.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Clinical attention does not belong to the patient");
    }

    @Test
    void createOdontogramFindingRejectsInvalidToothCodeForDentition() {
        CreateOdontogramFindingRequest request = new CreateOdontogramFindingRequest(
                null, DentitionType.ADULT, "99", ToothSurface.OCCLUSAL,
                ToothFinding.CARIOUS, null
        );

        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(userRepository.findById(dentist.getId())).thenReturn(Optional.of(dentist));

        assertThatThrownBy(() -> clinicalRecordService.createOdontogramFinding(patientA.getId(), request, dentist.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid tooth code");
    }

    @Test
    void findCurrentOdontogramBuildsChartOverlay() {
        when(patientRepository.existsById(patientA.getId())).thenReturn(true);
        OdontogramFinding finding = new OdontogramFinding(
                UUID.randomUUID(), patientA, null, dentist, DentitionType.ADULT,
                "16", ToothSurface.OCCLUSAL, ToothFinding.CARIOUS, "Caries", FIXED_NOW
        );
        when(odontogramFindingRepository.findByPatient_IdAndDentitionOrderByCreatedAtAsc(patientA.getId(), DentitionType.ADULT))
                .thenReturn(List.of(finding));

        OdontogramResponse response = clinicalRecordService.findCurrentOdontogram(patientA.getId(), DentitionType.ADULT);

        assertThat(response.dentition()).isEqualTo(DentitionType.ADULT);
        assertThat(response.teeth()).hasSize(32);
        assertThat(response.teeth().get("16")).isEqualTo(ToothFinding.CARIOUS);
        assertThat(response.teeth().get("11")).isEqualTo(ToothFinding.HEALTHY);
    }

    @Test
    void findClinicalHistoryCombinesAndSortsEntriesChronologically() {
        when(patientRepository.existsById(patientA.getId())).thenReturn(true);

        ClinicalAttention attention = new ClinicalAttention(
                UUID.randomUUID(), patientA, dentist, null, "Consulta regular", "Notas", null,
                FIXED_NOW.minusSeconds(3600), FIXED_NOW.minusSeconds(3600), FIXED_NOW.minusSeconds(3600)
        );
        ClinicalDiagnosis diagnosis = new ClinicalDiagnosis(
                UUID.randomUUID(), patientA, attention, null, dentist,
                DiagnosisType.PRIMARY, "Caries", FIXED_NOW.minusSeconds(1800)
        );
        ClinicalEvolutionNote evolution = new ClinicalEvolutionNote(
                UUID.randomUUID(), patientA, attention, dentist,
                LocalDate.of(2026, 10, 1), "Profilaxis", "Nota", FIXED_NOW
        );

        when(clinicalAttentionRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(attention)));
        when(clinicalDiagnosisRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(diagnosis)));
        when(clinicalEvolutionNoteRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(evolution)));
        when(odontogramFindingRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(treatmentProcedureRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(clinicalDocumentRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(clinicalPreparationRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ClinicalHistoryEntryResponse> history = clinicalRecordService.findClinicalHistory(
                patientA.getId(), 0, 10
        );

        assertThat(history.getContent()).hasSize(3);
        // Evolution is newest (FIXED_NOW), followed by diagnosis, followed by attention
        assertThat(history.getContent().get(0).category()).isEqualTo("EVOLUTION");
        assertThat(history.getContent().get(1).category()).isEqualTo("DIAGNOSIS");
        assertThat(history.getContent().get(2).category()).isEqualTo("ATTENTION");
    }

    @Test
    void findClinicalHistoryIncludesOnlyThePatientsTreatmentProcedureEvents() {
        when(patientRepository.existsById(patientA.getId())).thenReturn(true);
        when(patientRepository.existsById(patientB.getId())).thenReturn(true);
        stubEmptyClinicalHistory(patientA.getId());
        stubEmptyClinicalHistory(patientB.getId());

        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), patientA, dentist, "Plan aprobado", null,
                TreatmentPlanStatus.DRAFT, FIXED_NOW.minusSeconds(120), FIXED_NOW.minusSeconds(120));
        TreatmentPlanItem item = new TreatmentPlanItem(UUID.randomUUID(), "Endodoncia", "21", 1,
                new BigDecimal("1200.00"), 0);
        plan.addItem(item);
        plan.approve(FIXED_NOW.minusSeconds(90));
        TreatmentProcedure procedure = new TreatmentProcedure(UUID.randomUUID(), plan, item, patientA, dentist,
                item.getName(), item.getTooth(), 1, "Conductometría realizada",
                TreatmentProcedureStatus.IN_PROGRESS, FIXED_NOW.minusSeconds(60));
        procedure.complete("Sin complicaciones", FIXED_NOW);

        when(treatmentProcedureRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(procedure)));

        Page<ClinicalHistoryEntryResponse> patientAHistory =
                clinicalRecordService.findClinicalHistory(patientA.getId(), 0, 10);
        Page<ClinicalHistoryEntryResponse> patientBHistory =
                clinicalRecordService.findClinicalHistory(patientB.getId(), 0, 10);

        assertThat(patientAHistory.getContent()).extracting(ClinicalHistoryEntryResponse::category)
                .containsExactly("TREATMENT_PROCEDURE_COMPLETED", "TREATMENT_PROCEDURE_STARTED");
        assertThat(patientAHistory.getContent().get(0).action()).contains("Endodoncia");
        assertThat(patientAHistory.getContent().get(0).description())
                .contains("Pieza 21", "Conductometría realizada", "Sin complicaciones");
        assertThat(patientAHistory.getContent()).extracting(ClinicalHistoryEntryResponse::author)
                .containsOnly("Dr. Clinico");
        assertThat(patientBHistory).isEmpty();
    }

    private void stubEmptyClinicalHistory(UUID patientId) {
        when(clinicalAttentionRepository.findByPatient_Id(patientId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(clinicalDiagnosisRepository.findByPatient_Id(patientId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(clinicalEvolutionNoteRepository.findByPatient_Id(patientId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(odontogramFindingRepository.findByPatient_Id(patientId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(treatmentProcedureRepository.findByPatient_Id(patientId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(clinicalDocumentRepository.findByPatient_Id(patientId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(clinicalPreparationRepository.findByPatient_Id(patientId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void findSummaryByPatientReturnsAggregatedClinicalView() {
        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(patientRepository.existsById(patientA.getId())).thenReturn(true);
        when(medicalHistoryRepository.findByPatient_Id(patientA.getId())).thenReturn(Optional.empty());
        when(clinicalAttentionRepository.findFirstByPatient_IdOrderByOccurredAtDescCreatedAtDesc(patientA.getId()))
                .thenReturn(Optional.empty());
        when(clinicalDiagnosisRepository.findTop10ByPatient_IdOrderByCreatedAtDesc(patientA.getId()))
                .thenReturn(List.of());
        when(clinicalEvolutionNoteRepository.findFirstByPatient_IdOrderByConsultationDateDescCreatedAtDesc(patientA.getId()))
                .thenReturn(Optional.empty());
        when(odontogramFindingRepository.findByPatient_IdAndDentitionOrderByCreatedAtAsc(patientA.getId(), DentitionType.ADULT))
                .thenReturn(List.of());
        when(treatmentPlanRepository.findByPatient_Id(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ClinicalRecordSummaryResponse summary = clinicalRecordService.findSummaryByPatient(patientA.getId());

        assertThat(summary).isNotNull();
        assertThat(summary.patient().id()).isEqualTo(patientA.getId());
        assertThat(summary.currentOdontogram().dentition()).isEqualTo(DentitionType.ADULT);
    }

    @Test
    void findCurrentAttention_whenPatientHasNoAttention_returnsExplicitFalse() {
        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(clinicalAttentionRepository.findFirstByPatient_IdOrderByOccurredAtDescCreatedAtDesc(patientA.getId()))
                .thenReturn(Optional.empty());
        when(clinicalPreparationRepository.findFirstByPatient_IdOrderByCreatedAtDesc(patientA.getId()))
                .thenReturn(Optional.empty());
        when(medicalHistoryRepository.findByPatient_Id(patientA.getId())).thenReturn(Optional.empty());

        CurrentAttentionResponse response = clinicalRecordService.findCurrentAttention(patientA.getId());

        assertThat(response).isNotNull();
        assertThat(response.hasCurrentAttention()).isFalse();
        assertThat(response.currentAttention()).isNull();
        assertThat(response.patient().id()).isEqualTo(patientA.getId());
        assertThat(response.patient().code()).isEqualTo("EXP-001");
    }

    @Test
    void findCurrentAttention_whenPatientHasAttention_returnsConsolidatedDetail() {
        UUID attentionId = UUID.randomUUID();
        ClinicalAttention attention = new ClinicalAttention(
                attentionId, patientA, dentist, null, "Dolor molar", "Caries profunda", "Obturar",
                FIXED_NOW, FIXED_NOW, FIXED_NOW
        );

        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), patientA, dentist, "Plan integral", null,
                TreatmentPlanStatus.APPROVED, FIXED_NOW, FIXED_NOW);

        ClinicalDiagnosis diagnosis = new ClinicalDiagnosis(
                UUID.randomUUID(), patientA, attention, plan, dentist,
                DiagnosisType.PRIMARY, "Pulpitis reversible", FIXED_NOW
        );
        ClinicalEvolutionNote evolution = new ClinicalEvolutionNote(
                UUID.randomUUID(), patientA, attention, dentist,
                LocalDate.of(2026, 10, 1), "Apertura cameral", "Se coloca medicación", FIXED_NOW
        );
        OdontogramFinding finding = new OdontogramFinding(
                UUID.randomUUID(), patientA, attention, dentist,
                DentitionType.ADULT, "36", ToothSurface.OCCLUSAL,
                ToothFinding.CARIOUS, "Caries", FIXED_NOW
        );

        ClinicalPreparation preparation = new ClinicalPreparation(
                UUID.randomUUID(), patientA, attention, dentist,
                "120/80", 72, new BigDecimal("36.5"), new BigDecimal("68.50"),
                "Paciente normotenso", FIXED_NOW, FIXED_NOW
        );

        MedicalHistory history = new MedicalHistory(UUID.randomUUID(), patientA, FIXED_NOW, FIXED_NOW);
        history.replaceAllergies(List.of("Penicilina"));
        history.replaceCurrentMedications(List.of("Ibuprofeno"));

        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(clinicalAttentionRepository.findFirstByPatient_IdOrderByOccurredAtDescCreatedAtDesc(patientA.getId()))
                .thenReturn(Optional.of(attention));
        when(clinicalPreparationRepository.findFirstByPatient_IdOrderByCreatedAtDesc(patientA.getId()))
                .thenReturn(Optional.of(preparation));
        when(medicalHistoryRepository.findByPatient_Id(patientA.getId())).thenReturn(Optional.of(history));
        when(clinicalDiagnosisRepository.findByAttention_IdOrderByCreatedAtDesc(attentionId))
                .thenReturn(List.of(diagnosis));
        when(clinicalEvolutionNoteRepository.findByAttention_IdOrderByConsultationDateDescCreatedAtDesc(attentionId))
                .thenReturn(List.of(evolution));
        when(odontogramFindingRepository.findByAttention_IdOrderByCreatedAtDesc(attentionId))
                .thenReturn(List.of(finding));

        CurrentAttentionResponse response = clinicalRecordService.findCurrentAttention(patientA.getId());

        assertThat(response.hasCurrentAttention()).isTrue();
        assertThat(response.currentAttention()).isNotNull();
        assertThat(response.currentAttention().id()).isEqualTo(attentionId);
        assertThat(response.currentAttention().reason()).isEqualTo("Dolor molar");
        assertThat(response.currentAttention().diagnoses()).hasSize(1);
        assertThat(response.currentAttention().diagnoses().get(0).description()).isEqualTo("Pulpitis reversible");
        assertThat(response.currentAttention().evolutionNotes()).hasSize(1);
        assertThat(response.currentAttention().evolutionNotes().get(0).procedureSummary()).isEqualTo("Apertura cameral");
        assertThat(response.currentAttention().odontogramFindings()).hasSize(1);
        assertThat(response.currentAttention().treatmentPlan()).isNotNull();
        assertThat(response.currentAttention().treatmentPlan().name()).isEqualTo("Plan integral");

        assertThat(response.preparation()).isNotNull();
        assertThat(response.preparation().bloodPressure()).isEqualTo("120/80");
        assertThat(response.preparation().allergies()).containsExactly("Penicilina");
        assertThat(response.preparation().currentMedications()).containsExactly("Ibuprofeno");
    }

    @Test
    void findCurrentAttention_whenAppointmentCancelled_returnsFalse() {
        Appointment cancelledAppointment = new Appointment(
                UUID.randomUUID(), patientA, dentist, FIXED_NOW,
                AppointmentStatus.CANCELLED, FIXED_NOW, FIXED_NOW
        );

        ClinicalAttention attention = new ClinicalAttention(
                UUID.randomUUID(), patientA, dentist, cancelledAppointment, "Cita cancelada", "No se presentó", null,
                FIXED_NOW, FIXED_NOW, FIXED_NOW
        );

        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(clinicalAttentionRepository.findFirstByPatient_IdOrderByOccurredAtDescCreatedAtDesc(patientA.getId()))
                .thenReturn(Optional.of(attention));
        when(clinicalPreparationRepository.findFirstByPatient_IdOrderByCreatedAtDesc(patientA.getId()))
                .thenReturn(Optional.empty());
        when(medicalHistoryRepository.findByPatient_Id(patientA.getId())).thenReturn(Optional.empty());

        CurrentAttentionResponse response = clinicalRecordService.findCurrentAttention(patientA.getId());

        assertThat(response.hasCurrentAttention()).isFalse();
        assertThat(response.currentAttention()).isNull();
    }

    @Test
    void createPreparation_valid_savesAndReturnsSnapshotWithMedicalHistory() {
        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(userRepository.findById(dentist.getId())).thenReturn(Optional.of(dentist));

        MedicalHistory history = new MedicalHistory(UUID.randomUUID(), patientA, FIXED_NOW, FIXED_NOW);
        history.replaceAllergies(List.of("Latex"));
        history.replaceCurrentMedications(List.of("Amoxicilina"));
        when(medicalHistoryRepository.findByPatient_Id(patientA.getId())).thenReturn(Optional.of(history));

        when(clinicalPreparationRepository.save(any(ClinicalPreparation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateClinicalPreparationRequest request = new CreateClinicalPreparationRequest(
                null, "115/75", 68, new BigDecimal("36.4"), new BigDecimal("65.00"), "Triage pre-atención"
        );

        ClinicalPreparationResponse response = clinicalRecordService.createPreparation(
                patientA.getId(), null, request, dentist.getId()
        );

        assertThat(response).isNotNull();
        assertThat(response.patientId()).isEqualTo(patientA.getId());
        assertThat(response.bloodPressure()).isEqualTo("115/75");
        assertThat(response.heartRate()).isEqualTo(68);
        assertThat(response.temperature()).isEqualByComparingTo(new BigDecimal("36.4"));
        assertThat(response.weight()).isEqualByComparingTo(new BigDecimal("65.00"));
        assertThat(response.observations()).isEqualTo("Triage pre-atención");
        assertThat(response.allergies()).containsExactly("Latex");
        assertThat(response.currentMedications()).containsExactly("Amoxicilina");
    }

    @Test
    void createPreparation_mismatchedAttention_throwsNotFound() {
        UUID attentionId = UUID.randomUUID();
        when(patientRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(userRepository.findById(dentist.getId())).thenReturn(Optional.of(dentist));
        when(clinicalAttentionRepository.findByIdAndPatient_Id(attentionId, patientA.getId()))
                .thenReturn(Optional.empty());

        CreateClinicalPreparationRequest request = new CreateClinicalPreparationRequest(
                attentionId, "120/80", 70, new BigDecimal("36.5"), null, null
        );

        assertThatThrownBy(() -> clinicalRecordService.createPreparation(patientA.getId(), attentionId, request, dentist.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Clinical attention not found");
    }

    @Test
    void createDiagnosis_withPatientId_mismatchedAttention_throwsNotFound() {
        UUID attentionId = UUID.randomUUID();
        when(clinicalAttentionRepository.findByIdAndPatient_Id(attentionId, patientA.getId()))
                .thenReturn(Optional.empty());

        CreateClinicalDiagnosisRequest request = new CreateClinicalDiagnosisRequest(
                DiagnosisType.PRIMARY, "Gingivitis", null
        );

        assertThatThrownBy(() -> clinicalRecordService.createDiagnosis(patientA.getId(), attentionId, request, dentist.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Clinical attention not found");
    }

    @Test
    void createEvolutionNote_withPatientId_mismatchedAttention_throwsNotFound() {
        UUID attentionId = UUID.randomUUID();
        when(clinicalAttentionRepository.findByIdAndPatient_Id(attentionId, patientA.getId()))
                .thenReturn(Optional.empty());

        CreateEvolutionNoteRequest request = new CreateEvolutionNoteRequest(
                LocalDate.of(2026, 10, 1), "Profilaxis", "Nota"
        );

        assertThatThrownBy(() -> clinicalRecordService.createEvolutionNote(patientA.getId(), attentionId, request, dentist.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Clinical attention not found");
    }

    @Test
    void antiIdor_findMethods_throwNotFoundWhenPatientMismatch() {
        UUID resourceId = UUID.randomUUID();

        when(clinicalAttentionRepository.findByIdAndPatient_Id(resourceId, patientA.getId()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> clinicalRecordService.findAttentionById(patientA.getId(), resourceId))
                .isInstanceOf(ResourceNotFoundException.class);

        when(clinicalDiagnosisRepository.findByIdAndPatient_Id(resourceId, patientA.getId()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> clinicalRecordService.findDiagnosisById(patientA.getId(), resourceId))
                .isInstanceOf(ResourceNotFoundException.class);

        when(clinicalEvolutionNoteRepository.findByIdAndPatient_Id(resourceId, patientA.getId()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> clinicalRecordService.findEvolutionNoteById(patientA.getId(), resourceId))
                .isInstanceOf(ResourceNotFoundException.class);

        when(clinicalPreparationRepository.findByIdAndPatient_Id(resourceId, patientA.getId()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> clinicalRecordService.findPreparationById(patientA.getId(), resourceId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findClinicalHistory_includesDocumentsAndPreparationsWithoutCallingR2() {
        when(patientRepository.existsById(patientA.getId())).thenReturn(true);
        stubEmptyClinicalHistory(patientA.getId());

        ClinicalDocument document = new ClinicalDocument(
                UUID.randomUUID(), patientA, dentist, "Radiografía Panorámica",
                ClinicalDocumentType.RADIOGRAPHY, "Rx de control", LocalDate.of(2026, 10, 1),
                FIXED_NOW.minusSeconds(500), FIXED_NOW.minusSeconds(500),
                "patients/1/rx.png", "rx.png", 1024L, "image/png"
        );

        ClinicalPreparation prep = new ClinicalPreparation(
                UUID.randomUUID(), patientA, null, dentist,
                "120/80", 72, new BigDecimal("36.5"), new BigDecimal("70.00"),
                "Triage realizado", FIXED_NOW.minusSeconds(1000), FIXED_NOW.minusSeconds(1000)
        );

        when(clinicalDocumentRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(document)));
        when(clinicalPreparationRepository.findByPatient_Id(patientA.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(prep)));

        Page<ClinicalHistoryEntryResponse> history = clinicalRecordService.findClinicalHistory(
                patientA.getId(), 0, 10
        );

        assertThat(history.getContent()).hasSize(2);
        assertThat(history.getContent().get(0).category()).isEqualTo("DOCUMENT");
        assertThat(history.getContent().get(0).action()).contains("Radiografía Panorámica");
        assertThat(history.getContent().get(1).category()).isEqualTo("PREPARATION");
        assertThat(history.getContent().get(1).action()).contains("Preparación pre-atención");
        assertThat(history.getContent().get(1).description()).contains("P/A: 120/80", "FC: 72 lpm", "Temp: 36.5 °C");
    }
}
