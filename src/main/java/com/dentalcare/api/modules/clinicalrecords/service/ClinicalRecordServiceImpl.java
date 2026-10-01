package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalAttentionRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDiagnosisRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateEvolutionNoteRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateOdontogramFindingRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalAttentionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDiagnosisResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalEvolutionResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalHistoryEntryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalRecordSummaryResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramFindingResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.OdontogramResponse;
import com.dentalcare.api.modules.clinicalrecords.mapper.ClinicalRecordMapper;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalAttention;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDiagnosis;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalEvolutionNote;
import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.DiagnosisType;
import com.dentalcare.api.modules.clinicalrecords.model.OdontogramFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothValidator;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalAttentionRepository;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDiagnosisRepository;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalEvolutionNoteRepository;
import com.dentalcare.api.modules.clinicalrecords.repository.OdontogramFindingRepository;
import com.dentalcare.api.modules.medicalhistory.model.MedicalHistory;
import com.dentalcare.api.modules.medicalhistory.repository.MedicalHistoryRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ClinicalRecordServiceImpl implements ClinicalRecordService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort ATTENTION_ORDER = Sort.by(
            Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
    private static final Sort DIAGNOSIS_ORDER = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort EVOLUTION_ORDER = Sort.by(
            Sort.Order.desc("consultationDate"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort FINDING_ORDER = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ClinicalAttentionRepository clinicalAttentionRepository;
    private final ClinicalDiagnosisRepository clinicalDiagnosisRepository;
    private final ClinicalEvolutionNoteRepository clinicalEvolutionNoteRepository;
    private final OdontogramFindingRepository odontogramFindingRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final TreatmentPlanRepository treatmentPlanRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final ClinicalRecordMapper mapper;
    private final Clock clock;

    public ClinicalRecordServiceImpl(ClinicalAttentionRepository clinicalAttentionRepository,
                                     ClinicalDiagnosisRepository clinicalDiagnosisRepository,
                                     ClinicalEvolutionNoteRepository clinicalEvolutionNoteRepository,
                                     OdontogramFindingRepository odontogramFindingRepository,
                                     PatientRepository patientRepository,
                                     UserRepository userRepository,
                                     AppointmentRepository appointmentRepository,
                                     TreatmentPlanRepository treatmentPlanRepository,
                                     MedicalHistoryRepository medicalHistoryRepository,
                                     ClinicalRecordMapper mapper,
                                     Clock clock) {
        this.clinicalAttentionRepository = clinicalAttentionRepository;
        this.clinicalDiagnosisRepository = clinicalDiagnosisRepository;
        this.clinicalEvolutionNoteRepository = clinicalEvolutionNoteRepository;
        this.odontogramFindingRepository = odontogramFindingRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.treatmentPlanRepository = treatmentPlanRepository;
        this.medicalHistoryRepository = medicalHistoryRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ClinicalAttentionResponse createAttention(UUID patientId, CreateClinicalAttentionRequest request,
                                                     UUID authenticatedUserId) {
        requireId(patientId, "Patient id is required");
        requireId(authenticatedUserId, "Authentication is required");

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        User professional = findActiveUser(authenticatedUserId);

        Appointment appointment = null;
        if (request.appointmentId() != null) {
            appointment = appointmentRepository.findById(request.appointmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
            if (!appointment.getPatient().getId().equals(patientId)) {
                throw new BadRequestException("Appointment does not belong to the patient");
            }
        }

        Instant now = clock.instant();
        ClinicalAttention attention = new ClinicalAttention(
                UUID.randomUUID(),
                patient,
                professional,
                appointment,
                request.reason().trim(),
                request.clinicalNotes().trim(),
                request.nextSteps() != null ? request.nextSteps().trim() : null,
                now,
                now,
                now
        );

        ClinicalAttention saved = clinicalAttentionRepository.save(attention);
        return mapper.toAttentionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClinicalAttentionResponse> findAttentionsByPatient(UUID patientId, int page, int size) {
        requireId(patientId, "Patient id is required");
        ensurePatientExists(patientId);

        Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size), ATTENTION_ORDER);
        return clinicalAttentionRepository.findByPatient_Id(patientId, pageable)
                .map(mapper::toAttentionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalAttentionResponse findAttentionById(UUID attentionId) {
        requireId(attentionId, "Attention id is required");
        ClinicalAttention attention = clinicalAttentionRepository.findById(attentionId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical attention not found"));
        return mapper.toAttentionResponse(attention);
    }

    @Override
    @Transactional
    public ClinicalDiagnosisResponse createDiagnosis(UUID attentionId, CreateClinicalDiagnosisRequest request,
                                                     UUID authenticatedUserId) {
        requireId(attentionId, "Attention id is required");
        requireId(authenticatedUserId, "Authentication is required");

        ClinicalAttention attention = clinicalAttentionRepository.findById(attentionId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical attention not found"));

        User author = findActiveUser(authenticatedUserId);

        TreatmentPlan treatmentPlan = null;
        if (request.treatmentPlanId() != null) {
            treatmentPlan = treatmentPlanRepository.findById(request.treatmentPlanId())
                    .orElseThrow(() -> new ResourceNotFoundException("Treatment plan not found"));
            if (!treatmentPlan.getPatient().getId().equals(attention.getPatient().getId())) {
                throw new BadRequestException("Treatment plan does not belong to the patient");
            }
        }

        Instant now = clock.instant();
        ClinicalDiagnosis diagnosis = new ClinicalDiagnosis(
                UUID.randomUUID(),
                attention.getPatient(),
                attention,
                treatmentPlan,
                author,
                request.type(),
                request.description().trim(),
                now
        );

        ClinicalDiagnosis saved = clinicalDiagnosisRepository.save(diagnosis);
        return mapper.toDiagnosisResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClinicalDiagnosisResponse> findDiagnosesByPatient(UUID patientId, DiagnosisType type, int page, int size) {
        requireId(patientId, "Patient id is required");
        ensurePatientExists(patientId);

        Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size), DIAGNOSIS_ORDER);
        Page<ClinicalDiagnosis> diagnoses = (type != null)
                ? clinicalDiagnosisRepository.findByPatient_IdAndType(patientId, type, pageable)
                : clinicalDiagnosisRepository.findByPatient_Id(patientId, pageable);

        return diagnoses.map(mapper::toDiagnosisResponse);
    }

    @Override
    @Transactional
    public ClinicalEvolutionResponse createEvolutionNote(UUID attentionId, CreateEvolutionNoteRequest request,
                                                         UUID authenticatedUserId) {
        requireId(attentionId, "Attention id is required");
        requireId(authenticatedUserId, "Authentication is required");

        ClinicalAttention attention = clinicalAttentionRepository.findById(attentionId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical attention not found"));

        User author = findActiveUser(authenticatedUserId);

        Instant now = clock.instant();
        ClinicalEvolutionNote note = new ClinicalEvolutionNote(
                UUID.randomUUID(),
                attention.getPatient(),
                attention,
                author,
                request.consultationDate(),
                request.procedureSummary().trim(),
                request.note().trim(),
                now
        );

        ClinicalEvolutionNote saved = clinicalEvolutionNoteRepository.save(note);
        return mapper.toEvolutionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClinicalEvolutionResponse> findEvolutionNotesByPatient(UUID patientId, int page, int size) {
        requireId(patientId, "Patient id is required");
        ensurePatientExists(patientId);

        Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size), EVOLUTION_ORDER);
        return clinicalEvolutionNoteRepository.findByPatient_Id(patientId, pageable)
                .map(mapper::toEvolutionResponse);
    }

    @Override
    @Transactional
    public OdontogramFindingResponse createOdontogramFinding(UUID patientId, CreateOdontogramFindingRequest request,
                                                             UUID authenticatedUserId) {
        requireId(patientId, "Patient id is required");
        requireId(authenticatedUserId, "Authentication is required");

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        User author = findActiveUser(authenticatedUserId);

        ClinicalAttention attention = null;
        if (request.attentionId() != null) {
            attention = clinicalAttentionRepository.findById(request.attentionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Clinical attention not found"));
            if (!attention.getPatient().getId().equals(patientId)) {
                throw new BadRequestException("Clinical attention does not belong to the patient");
            }
        }

        if (!ToothValidator.isValidTooth(request.dentition(), request.toothCode())) {
            throw new BadRequestException("Invalid tooth code " + request.toothCode() + " for dentition " + request.dentition());
        }

        Instant now = clock.instant();
        OdontogramFinding finding = new OdontogramFinding(
                UUID.randomUUID(),
                patient,
                attention,
                author,
                request.dentition(),
                request.toothCode().trim(),
                request.surface(),
                request.finding(),
                request.observation() != null ? request.observation().trim() : null,
                now
        );

        OdontogramFinding saved = odontogramFindingRepository.save(finding);
        return mapper.toFindingResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OdontogramResponse findCurrentOdontogram(UUID patientId, DentitionType dentition) {
        requireId(patientId, "Patient id is required");
        ensurePatientExists(patientId);

        DentitionType targetDentition = dentition != null ? dentition : DentitionType.ADULT;
        List<OdontogramFinding> findings = odontogramFindingRepository
                .findByPatient_IdAndDentitionOrderByCreatedAtAsc(patientId, targetDentition);

        return mapper.toOdontogramResponse(targetDentition, findings);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OdontogramFindingResponse> findOdontogramFindingsByPatient(UUID patientId, int page, int size) {
        requireId(patientId, "Patient id is required");
        ensurePatientExists(patientId);

        Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size), FINDING_ORDER);
        return odontogramFindingRepository.findByPatient_Id(patientId, pageable)
                .map(mapper::toFindingResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClinicalHistoryEntryResponse> findClinicalHistory(UUID patientId, int page, int size) {
        requireId(patientId, "Patient id is required");
        ensurePatientExists(patientId);

        List<ClinicalHistoryEntryResponse> allEntries = new ArrayList<>();

        clinicalAttentionRepository.findByPatient_Id(patientId, Pageable.unpaged())
                .forEach(a -> allEntries.add(mapper.fromAttention(a)));
        clinicalDiagnosisRepository.findByPatient_Id(patientId, Pageable.unpaged())
                .forEach(d -> allEntries.add(mapper.fromDiagnosis(d)));
        clinicalEvolutionNoteRepository.findByPatient_Id(patientId, Pageable.unpaged())
                .forEach(e -> allEntries.add(mapper.fromEvolution(e)));
        odontogramFindingRepository.findByPatient_Id(patientId, Pageable.unpaged())
                .forEach(f -> allEntries.add(mapper.fromFinding(f)));

        allEntries.sort(Comparator.comparing(ClinicalHistoryEntryResponse::timestamp).reversed()
                .thenComparing(ClinicalHistoryEntryResponse::id));

        int safePage = Math.max(0, page);
        int safeSize = clampPageSize(size);
        int start = Math.min(safePage * safeSize, allEntries.size());
        int end = Math.min(start + safeSize, allEntries.size());

        List<ClinicalHistoryEntryResponse> pageContent = allEntries.subList(start, end);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return new PageImpl<>(pageContent, pageable, allEntries.size());
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalRecordSummaryResponse findSummaryByPatient(UUID patientId) {
        requireId(patientId, "Patient id is required");
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatient_Id(patientId).orElse(null);
        ClinicalAttention latestAttention = clinicalAttentionRepository
                .findFirstByPatient_IdOrderByOccurredAtDescCreatedAtDesc(patientId).orElse(null);
        List<ClinicalDiagnosis> recentDiagnoses = clinicalDiagnosisRepository
                .findTop10ByPatient_IdOrderByCreatedAtDesc(patientId);
        ClinicalEvolutionNote latestEvolution = clinicalEvolutionNoteRepository
                .findFirstByPatient_IdOrderByConsultationDateDescCreatedAtDesc(patientId).orElse(null);
        OdontogramResponse currentOdontogram = findCurrentOdontogram(patientId, DentitionType.ADULT);

        Page<TreatmentPlan> plansPage = treatmentPlanRepository.findByPatient_Id(
                patientId, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        return mapper.toSummaryResponse(
                patient,
                medicalHistory,
                latestAttention,
                recentDiagnoses,
                latestEvolution,
                currentOdontogram,
                plansPage.getContent()
        );
    }

    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Authenticated user is not active");
        }
        return user;
    }

    private void ensurePatientExists(UUID patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
    }

    private static int clampPageSize(int size) {
        if (size < 1) return 20;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static void requireId(UUID id, String message) {
        if (id == null) {
            throw new BadRequestException(message);
        }
    }
}
