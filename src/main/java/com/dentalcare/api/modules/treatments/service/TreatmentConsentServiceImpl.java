package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.treatments.dto.request.PrepareTreatmentConsentRequest;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentConsentResponse;
import com.dentalcare.api.modules.treatments.mapper.TreatmentConsentMapper;
import com.dentalcare.api.modules.treatments.model.TreatmentBudget;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentConsent;
import com.dentalcare.api.modules.treatments.model.TreatmentConsentStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentBudgetRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentConsentRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TreatmentConsentServiceImpl implements TreatmentConsentService {
    private static final List<TreatmentConsentStatus> ACTIVE_STATUSES =
            List.of(TreatmentConsentStatus.PENDING, TreatmentConsentStatus.ACCEPTED);

    private final TreatmentConsentRepository consents;
    private final TreatmentBudgetRepository budgets;
    private final TreatmentPlanRepository plans;
    private final UserRepository users;
    private final TreatmentConsentMapper mapper;
    private final Clock clock;

    public TreatmentConsentServiceImpl(TreatmentConsentRepository consents,
                                       TreatmentBudgetRepository budgets,
                                       TreatmentPlanRepository plans,
                                       UserRepository users,
                                       TreatmentConsentMapper mapper,
                                       Clock clock) {
        this.consents = consents;
        this.budgets = budgets;
        this.plans = plans;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TreatmentConsentResponse prepare(UUID planId, UUID actorId,
                                            PrepareTreatmentConsentRequest request) {
        requireId(planId, "Treatment plan id is required");
        if (request == null) throw new BadRequestException("Treatment consent data is required");
        User actor = findActiveActor(actorId);
        TreatmentPlan plan = plans.findDetailedByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment plan not found"));
        if (plan.getStatus() != TreatmentPlanStatus.APPROVED) {
            throw new ConflictException("Only approved treatment plans can prepare a consent");
        }
        if (consents.findActiveByPlanIdForUpdate(planId, ACTIVE_STATUSES).isPresent()) {
            throw new ConflictException("Treatment plan already has an active consent");
        }
        TreatmentBudget budget = budgets
                .findFirstByTreatmentPlan_IdAndStatusOrderByVersionDesc(planId, TreatmentBudgetStatus.APPROVED)
                .orElse(null);
        Instant now = clock.instant();
        TreatmentConsent consent = new TreatmentConsent(
                UUID.randomUUID(), plan, plan.getPatient(), budget,
                required(request.documentVersion()), required(request.consentText()), actor, now);
        return mapper.toResponse(consents.saveAndFlush(consent));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TreatmentConsentResponse> findByPlan(UUID planId) {
        requireId(planId, "Treatment plan id is required");
        if (!plans.existsById(planId)) throw new ResourceNotFoundException("Treatment plan not found");
        return consents.findByTreatmentPlan_IdOrderByCreatedAtDesc(planId).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TreatmentConsentResponse findById(UUID consentId) {
        requireId(consentId, "Treatment consent id is required");
        return consents.findDetailedById(consentId).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment consent not found"));
    }

    @Override
    @Transactional
    public TreatmentConsentResponse accept(UUID consentId, UUID actorId) {
        TreatmentConsent consent = findForUpdate(consentId);
        if (consent.getStatus() != TreatmentConsentStatus.PENDING) {
            throw new ConflictException("Only pending treatment consents can be accepted");
        }
        consent.accept(findActiveActor(actorId), clock.instant());
        return mapper.toResponse(consents.saveAndFlush(consent));
    }

    @Override
    @Transactional
    public TreatmentConsentResponse revoke(UUID consentId, UUID actorId) {
        TreatmentConsent consent = findForUpdate(consentId);
        if (consent.getStatus() == TreatmentConsentStatus.REVOKED) {
            throw new ConflictException("Treatment consent is already revoked");
        }
        consent.revoke(findActiveActor(actorId), clock.instant());
        return mapper.toResponse(consents.saveAndFlush(consent));
    }

    private TreatmentConsent findForUpdate(UUID consentId) {
        requireId(consentId, "Treatment consent id is required");
        return consents.findDetailedByIdForUpdate(consentId)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment consent not found"));
    }

    private User findActiveActor(UUID actorId) {
        requireId(actorId, "Authentication is required");
        User actor = users.findById(actorId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        if (actor.getStatus() != UserStatus.ACTIVE) throw new AccessDeniedException("Active user is required");
        return actor;
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new BadRequestException("Required text must not be blank");
        return value.trim();
    }

    private void requireId(UUID id, String message) {
        if (id == null) throw new BadRequestException(message);
    }
}
