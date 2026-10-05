package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.dto.request.CompleteTreatmentProcedureRequest;
import com.dentalcare.api.modules.treatments.dto.request.CreateTreatmentProcedureRequest;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentProcedureResponse;
import com.dentalcare.api.modules.treatments.mapper.TreatmentProcedureMapper;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TreatmentProcedureServiceImpl implements TreatmentProcedureService {
    @org.springframework.beans.factory.annotation.Autowired(required = false) private AuditService auditService;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort HISTORY_ORDER = Sort.by(
            Sort.Order.desc("performedAt"), Sort.Order.desc("id"));

    private final TreatmentProcedureRepository procedures;
    private final TreatmentPlanRepository plans;
    private final PatientRepository patients;
    private final UserRepository users;
    private final TreatmentProcedureMapper mapper;
    private final Clock clock;

    public TreatmentProcedureServiceImpl(TreatmentProcedureRepository procedures,
                                         TreatmentPlanRepository plans,
                                         PatientRepository patients,
                                         UserRepository users,
                                         TreatmentProcedureMapper mapper,
                                         Clock clock) {
        this.procedures = procedures;
        this.plans = plans;
        this.patients = patients;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TreatmentProcedureResponse register(UUID planId, UUID professionalId,
                                               CreateTreatmentProcedureRequest request) {
        requireId(planId, "Treatment plan id is required");
        requireId(professionalId, "Authentication is required");
        if (request == null) throw new BadRequestException("Treatment procedure data is required");
        requireId(request.treatmentPlanItemId(), "Treatment plan item id is required");

        TreatmentPlan plan = plans.findDetailedByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment plan not found"));
        if (plan.getStatus() != TreatmentPlanStatus.APPROVED) {
            throw new ConflictException("Only approved treatment plans can be executed");
        }
        TreatmentPlanItem item = plan.getItems().stream()
                .filter(candidate -> candidate.getId().equals(request.treatmentPlanItemId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Treatment plan item not found"));
        User professional = findActiveDentist(professionalId);

        if (procedures.existsByTreatmentPlanItem_IdAndStatus(item.getId(),
                TreatmentProcedureStatus.IN_PROGRESS)) {
            throw new ConflictException("Treatment plan item already has a procedure in progress");
        }
        long completed = procedures.countByTreatmentPlanItem_IdAndStatus(
                item.getId(), TreatmentProcedureStatus.COMPLETED);
        if (completed >= item.getQuantity()) {
            throw new ConflictException("Planned procedure quantity is already fully completed");
        }

        TreatmentProcedure procedure = new TreatmentProcedure(
                UUID.randomUUID(), plan, item, plan.getPatient(), professional,
                item.getName(), item.getTooth(), Math.toIntExact(completed + 1),
                optional(request.clinicalObservations()), TreatmentProcedureStatus.IN_PROGRESS,
                clock.instant());
        var saved=procedures.saveAndFlush(procedure);
        if(auditService!=null)auditService.success(AuditActions.TREATMENT_PROCEDURE_REGISTERED,"TREATMENTS","TreatmentProcedure",saved.getId(),professionalId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TreatmentProcedureResponse complete(UUID procedureId, UUID professionalId,
                                               CompleteTreatmentProcedureRequest request) {
        requireId(procedureId, "Treatment procedure id is required");
        requireId(professionalId, "Authentication is required");
        User professional = findActiveDentist(professionalId);
        TreatmentProcedure procedure = procedures.findDetailedByIdForUpdate(procedureId)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment procedure not found"));
        if (procedure.getStatus() != TreatmentProcedureStatus.IN_PROGRESS) {
            throw new ConflictException("Treatment procedure is already completed");
        }
        if (!procedure.getProfessional().getId().equals(professional.getId())) {
            throw new AccessDeniedException("Only the professional who started the procedure can complete it");
        }
        procedure.complete(optional(request == null ? null : request.completionNotes()), clock.instant());
        var saved=procedures.saveAndFlush(procedure);
        if(auditService!=null)auditService.success(AuditActions.TREATMENT_PROCEDURE_COMPLETED,"TREATMENTS","TreatmentProcedure",saved.getId(),professionalId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TreatmentProcedureResponse findById(UUID procedureId) {
        requireId(procedureId, "Treatment procedure id is required");
        return procedures.findDetailedById(procedureId).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment procedure not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TreatmentProcedureResponse> findByPlan(UUID planId, int page, int size) {
        requireId(planId, "Treatment plan id is required");
        validatePage(page, size);
        if (!plans.existsById(planId)) throw new ResourceNotFoundException("Treatment plan not found");
        return procedures.findByTreatmentPlan_Id(planId, pageable(page, size)).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TreatmentProcedureResponse> findByPatient(UUID patientId, int page, int size) {
        requireId(patientId, "Patient id is required");
        validatePage(page, size);
        if (!patients.existsById(patientId)) throw new ResourceNotFoundException("Patient not found");
        return procedures.findByPatient_Id(patientId, pageable(page, size)).map(mapper::toResponse);
    }

    private User findActiveDentist(UUID userId) {
        User professional = users.findWithRolesById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        boolean activeDentist = professional.getStatus() == UserStatus.ACTIVE
                && professional.getRoles().stream()
                .anyMatch(role -> role.isActive() && "DENTIST".equals(role.getCode()));
        if (!activeDentist) throw new AccessDeniedException("An active dentist is required");
        return professional;
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), HISTORY_ORDER);
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new BadRequestException("Page must be at least 0");
        if (size < 1) throw new BadRequestException("Size must be at least 1");
    }

    private void requireId(UUID id, String message) {
        if (id == null) throw new BadRequestException(message);
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
