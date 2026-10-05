package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentBudgetResponse;
import com.dentalcare.api.modules.treatments.mapper.PatientTreatmentBudgetMapper;
import com.dentalcare.api.modules.treatments.model.PatientBudgetDecision;
import com.dentalcare.api.modules.treatments.model.TreatmentBudget;
import com.dentalcare.api.modules.treatments.repository.TreatmentBudgetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PatientTreatmentBudgetServiceImpl implements PatientTreatmentBudgetService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PatientRepository patients;
    private final TreatmentBudgetRepository budgets;
    private final PatientTreatmentBudgetMapper mapper;
    private final Clock clock;

    public PatientTreatmentBudgetServiceImpl(PatientRepository patients, TreatmentBudgetRepository budgets,
                                             PatientTreatmentBudgetMapper mapper, Clock clock) {
        this.patients = patients;
        this.budgets = budgets;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientTreatmentBudgetResponse> findMine(UUID userId, int page, int size) {
        Patient patient = patientForUser(userId);
        validatePage(page, size);
        Page<UUID> ids = budgets.findPublishedIdsByPatientId(
                patient.getId(), PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), ORDER));
        if (ids.isEmpty()) return new PageImpl<>(List.of(), ids.getPageable(), ids.getTotalElements());
        Map<UUID, TreatmentBudget> byId = budgets.findPatientDetailedByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(TreatmentBudget::getId, Function.identity()));
        List<PatientTreatmentBudgetResponse> content = ids.getContent().stream().map(byId::get)
                .filter(java.util.Objects::nonNull).map(mapper::toResponse).toList();
        return new PageImpl<>(content, ids.getPageable(), ids.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PatientTreatmentBudgetResponse findMineById(UUID userId, UUID budgetId) {
        Patient patient = patientForUser(userId);
        requireId(budgetId, "Treatment budget id is required");
        return mapper.toResponse(findOwned(patient.getId(), budgetId, false));
    }

    @Override
    @Transactional
    public PatientTreatmentBudgetResponse accept(UUID userId, UUID budgetId) {
        return decide(userId, budgetId, PatientBudgetDecision.ACCEPTED);
    }

    @Override
    @Transactional
    public PatientTreatmentBudgetResponse reject(UUID userId, UUID budgetId) {
        return decide(userId, budgetId, PatientBudgetDecision.REJECTED);
    }

    private PatientTreatmentBudgetResponse decide(UUID userId, UUID budgetId, PatientBudgetDecision decision) {
        Patient patient = patientForUser(userId);
        requireId(budgetId, "Treatment budget id is required");
        TreatmentBudget budget = findOwned(patient.getId(), budgetId, true);
        if (budget.getPatientDecision() != PatientBudgetDecision.PENDING) {
            throw new ConflictException("Treatment budget already has a patient decision");
        }
        if (decision == PatientBudgetDecision.ACCEPTED) budget.acceptByPatient(patient.getUser(), clock.instant());
        else budget.rejectByPatient(patient.getUser(), clock.instant());
        return mapper.toResponse(budgets.saveAndFlush(budget));
    }

    private TreatmentBudget findOwned(UUID patientId, UUID budgetId, boolean lock) {
        return (lock ? budgets.findPublishedOwnedByIdForUpdate(budgetId, patientId)
                : budgets.findPublishedOwnedById(budgetId, patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Treatment budget not found"));
    }

    private Patient patientForUser(UUID userId) {
        requireId(userId, "Authentication is required");
        Patient patient = patients.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        if (patient.getUser() == null) throw new ResourceNotFoundException("Patient not found");
        return patient;
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new BadRequestException("Page must be at least 0");
        if (size < 1) throw new BadRequestException("Size must be at least 1");
    }

    private void requireId(UUID id, String message) {
        if (id == null) throw new BadRequestException(message);
    }
}
