package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentPlanResponse;
import com.dentalcare.api.modules.treatments.mapper.PatientTreatmentPlanMapper;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentProcedureRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PatientTreatmentPlanServiceImpl implements PatientTreatmentPlanService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PatientRepository patients;
    private final TreatmentPlanRepository plans;
    private final TreatmentProcedureRepository procedures;
    private final PatientTreatmentPlanMapper mapper;

    public PatientTreatmentPlanServiceImpl(PatientRepository patients,
                                           TreatmentPlanRepository plans,
                                           TreatmentProcedureRepository procedures,
                                           PatientTreatmentPlanMapper mapper) {
        this.patients = patients;
        this.plans = plans;
        this.procedures = procedures;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientTreatmentPlanResponse> findMine(UUID authenticatedUserId, int page, int size) {
        Patient patient = patientForUser(authenticatedUserId);
        validatePage(page, size);
        Page<UUID> ids = plans.findApprovedIdsByPatientId(
                patient.getId(), PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), ORDER));
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), ids.getPageable(), ids.getTotalElements());
        }
        List<UUID> orderedIds = ids.getContent();
        Map<UUID, TreatmentPlan> planById = plans.findDetailedByIdIn(orderedIds).stream()
                .collect(Collectors.toMap(TreatmentPlan::getId, Function.identity()));
        Map<UUID, List<TreatmentProcedure>> procedureByPlan = procedures.findByTreatmentPlanIds(orderedIds)
                .stream().collect(Collectors.groupingBy(value -> value.getTreatmentPlan().getId()));
        List<PatientTreatmentPlanResponse> content = orderedIds.stream()
                .map(planById::get)
                .filter(java.util.Objects::nonNull)
                .map(plan -> mapper.toResponse(
                        plan, procedureByPlan.getOrDefault(plan.getId(), List.of())))
                .toList();
        return new PageImpl<>(content, ids.getPageable(), ids.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PatientTreatmentPlanResponse findMineById(UUID authenticatedUserId, UUID planId) {
        Patient patient = patientForUser(authenticatedUserId);
        requireId(planId, "Treatment plan id is required");
        TreatmentPlan plan = plans.findApprovedOwnedById(planId, patient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Treatment plan not found"));
        return mapper.toResponse(plan, procedures.findOwnedByPlanId(planId, patient.getId()));
    }

    private Patient patientForUser(UUID authenticatedUserId) {
        requireId(authenticatedUserId, "Authentication is required");
        return patients.findByUser_Id(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new BadRequestException("Page must be at least 0");
        if (size < 1) throw new BadRequestException("Size must be at least 1");
    }

    private void requireId(UUID value, String message) {
        if (value == null) throw new BadRequestException(message);
    }
}
