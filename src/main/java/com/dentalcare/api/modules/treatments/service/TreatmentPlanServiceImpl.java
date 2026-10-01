package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.treatments.dto.request.CreateTreatmentPlanRequest;
import com.dentalcare.api.modules.treatments.dto.request.TreatmentPlanItemRequest;
import com.dentalcare.api.modules.treatments.dto.request.UpdateTreatmentPlanRequest;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanResponse;
import com.dentalcare.api.modules.treatments.mapper.TreatmentPlanMapper;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TreatmentPlanServiceImpl implements TreatmentPlanService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String DENTIST_ROLE = "DENTIST";
    private static final Sort PLAN_ORDER = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TreatmentPlanRepository treatmentPlanRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final TreatmentPlanMapper treatmentPlanMapper;
    private final Clock clock;

    public TreatmentPlanServiceImpl(TreatmentPlanRepository treatmentPlanRepository,
                                    PatientRepository patientRepository,
                                    UserRepository userRepository,
                                    TreatmentPlanMapper treatmentPlanMapper,
                                    Clock clock) {
        this.treatmentPlanRepository = treatmentPlanRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.treatmentPlanMapper = treatmentPlanMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TreatmentPlanResponse> findByPatient(UUID patientId, int page, int size) {
        requireId(patientId, "Patient id is required");
        validatePage(page, size);
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), PLAN_ORDER);
        return treatmentPlanRepository.findByPatient_Id(patientId, pageable)
                .map(treatmentPlanMapper::toResponse);
    }

    @Override
    @Transactional
    public TreatmentPlanResponse create(UUID patientId, CreateTreatmentPlanRequest request) {
        requireId(patientId, "Patient id is required");
        requireRequest(request);
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        User professional = findActiveDentist(request.professionalId());
        Instant now = clock.instant();
        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), patient, professional,
                normalizeRequired(request.name()), normalizeOptional(request.observations()),
                TreatmentPlanStatus.DRAFT, now, now);
        plan.replaceItems(toItems(request.items()));
        return treatmentPlanMapper.toResponse(treatmentPlanRepository.saveAndFlush(plan));
    }

    @Override
    @Transactional(readOnly = true)
    public TreatmentPlanResponse findById(UUID planId) {
        requireId(planId, "Treatment plan id is required");
        return treatmentPlanRepository.findDetailedById(planId)
                .map(treatmentPlanMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment plan not found"));
    }

    @Override
    @Transactional
    public TreatmentPlanResponse update(UUID planId, UpdateTreatmentPlanRequest request) {
        requireId(planId, "Treatment plan id is required");
        requireRequest(request);
        TreatmentPlan plan = findForUpdate(planId);
        if (plan.getStatus() != TreatmentPlanStatus.DRAFT) {
            throw new ConflictException("Approved treatment plans cannot be updated");
        }
        User professional = findActiveDentist(request.professionalId());
        plan.update(normalizeRequired(request.name()), normalizeOptional(request.observations()),
                professional, clock.instant());
        plan.clearItems();
        treatmentPlanRepository.deleteItemsByTreatmentPlanId(plan.getId());
        plan = findForUpdate(planId);
        plan.replaceItems(toItems(request.items()));
        return treatmentPlanMapper.toResponse(treatmentPlanRepository.saveAndFlush(plan));
    }

    @Override
    @Transactional
    public TreatmentPlanResponse approve(UUID planId) {
        requireId(planId, "Treatment plan id is required");
        TreatmentPlan plan = findForUpdate(planId);
        if (plan.getStatus() != TreatmentPlanStatus.DRAFT) {
            throw new ConflictException("Treatment plan is already approved");
        }
        plan.approve(clock.instant());
        return treatmentPlanMapper.toResponse(treatmentPlanRepository.saveAndFlush(plan));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TreatmentPlanProfessionalResponse> findProfessionals() {
        return userRepository.findActiveDentists().stream()
                .map(user -> new TreatmentPlanProfessionalResponse(user.getId(), user.getFullName()))
                .toList();
    }

    private TreatmentPlan findForUpdate(UUID planId) {
        return treatmentPlanRepository.findDetailedByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment plan not found"));
    }

    private User findActiveDentist(UUID professionalId) {
        requireId(professionalId, "Professional id is required");
        User professional = userRepository.findWithRolesById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found"));
        boolean activeDentist = professional.getStatus() == UserStatus.ACTIVE
                && professional.getRoles().stream()
                .anyMatch(role -> role.isActive() && DENTIST_ROLE.equals(role.getCode()));
        if (!activeDentist) {
            throw new ConflictException("Professional is not an active dentist");
        }
        return professional;
    }

    private List<TreatmentPlanItem> toItems(List<TreatmentPlanItemRequest> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > 100) {
            throw new BadRequestException("Treatment plan must contain between 1 and 100 items");
        }
        return java.util.stream.IntStream.range(0, requests.size())
                .mapToObj(position -> {
                    TreatmentPlanItemRequest item = requests.get(position);
                    if (item == null || item.quantity() == null || item.quantity() <= 0
                            || item.unitPrice() == null || item.unitPrice().signum() <= 0) {
                        throw new BadRequestException("Treatment plan contains an invalid item");
                    }
                    return new TreatmentPlanItem(UUID.randomUUID(), normalizeRequired(item.name()),
                            normalizeOptional(item.tooth()), item.quantity(), item.unitPrice(), position);
                }).toList();
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Required text must not be blank");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new BadRequestException("Page must be at least 0");
        if (size < 1) throw new BadRequestException("Size must be at least 1");
    }

    private void requireId(UUID id, String message) {
        if (id == null) throw new BadRequestException(message);
    }

    private void requireRequest(Object request) {
        if (request == null) throw new BadRequestException("Treatment plan is required");
    }
}
