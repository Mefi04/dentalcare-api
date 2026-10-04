package com.dentalcare.api.modules.treatments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentBudgetResponse;
import com.dentalcare.api.modules.treatments.mapper.TreatmentBudgetMapper;
import com.dentalcare.api.modules.treatments.model.TreatmentBudget;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetItem;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetStatus;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.treatments.repository.TreatmentBudgetRepository;
import com.dentalcare.api.modules.treatments.repository.TreatmentPlanRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TreatmentBudgetServiceImpl implements TreatmentBudgetService {
    private static final List<TreatmentBudgetStatus> ACTIVE_STATUSES =
            List.of(TreatmentBudgetStatus.PENDING, TreatmentBudgetStatus.APPROVED);

    private final TreatmentBudgetRepository budgets;
    private final TreatmentPlanRepository plans;
    private final UserRepository users;
    private final TreatmentBudgetMapper mapper;
    private final Clock clock;

    public TreatmentBudgetServiceImpl(TreatmentBudgetRepository budgets,
                                      TreatmentPlanRepository plans,
                                      UserRepository users,
                                      TreatmentBudgetMapper mapper,
                                      Clock clock) {
        this.budgets = budgets;
        this.plans = plans;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TreatmentBudgetResponse generate(UUID planId, UUID actorId) {
        requireId(planId, "Treatment plan id is required");
        User actor = findActiveActor(actorId);
        TreatmentPlan plan = plans.findDetailedByIdForUpdate(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment plan not found"));
        if (plan.getStatus() != TreatmentPlanStatus.APPROVED) {
            throw new ConflictException("Only approved treatment plans can generate a budget");
        }
        if (plan.getItems().isEmpty()) {
            throw new ConflictException("Treatment plan has no items to quote");
        }
        if (budgets.findActiveByPlanIdForUpdate(planId, ACTIVE_STATUSES).isPresent()) {
            throw new ConflictException("Treatment plan already has an active budget");
        }

        List<TreatmentBudgetItem> snapshots = plan.getItems().stream()
                .map(this::snapshot)
                .toList();
        BigDecimal subtotal = snapshots.stream()
                .map(TreatmentBudgetItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = clock.instant();
        TreatmentBudget budget = new TreatmentBudget(
                UUID.randomUUID(), plan, plan.getPatient(),
                budgets.findMaxVersionByPlanId(planId) + 1,
                plan.getUpdatedAt(), subtotal, subtotal, actor, now);
        budget.replaceItems(snapshots);
        return mapper.toResponse(budgets.saveAndFlush(budget));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TreatmentBudgetResponse> findByPlan(UUID planId) {
        requireId(planId, "Treatment plan id is required");
        if (!plans.existsById(planId)) throw new ResourceNotFoundException("Treatment plan not found");
        return budgets.findByTreatmentPlan_IdOrderByVersionDesc(planId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TreatmentBudgetResponse findById(UUID budgetId) {
        requireId(budgetId, "Treatment budget id is required");
        return budgets.findDetailedById(budgetId).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment budget not found"));
    }

    @Override
    @Transactional
    public TreatmentBudgetResponse approve(UUID budgetId, UUID actorId) {
        TreatmentBudget budget = findPendingForDecision(budgetId);
        budget.approve(findActiveActor(actorId), clock.instant());
        return mapper.toResponse(budgets.saveAndFlush(budget));
    }

    @Override
    @Transactional
    public TreatmentBudgetResponse reject(UUID budgetId, UUID actorId) {
        TreatmentBudget budget = findPendingForDecision(budgetId);
        budget.reject(findActiveActor(actorId), clock.instant());
        return mapper.toResponse(budgets.saveAndFlush(budget));
    }

    private TreatmentBudget findPendingForDecision(UUID budgetId) {
        requireId(budgetId, "Treatment budget id is required");
        TreatmentBudget budget = budgets.findDetailedByIdForUpdate(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment budget not found"));
        if (budget.getStatus() != TreatmentBudgetStatus.PENDING) {
            throw new ConflictException("Only pending treatment budgets can be decided");
        }
        if (budget.getTreatmentPlan().getStatus() != TreatmentPlanStatus.APPROVED
                || !budget.getPlanUpdatedAt().equals(budget.getTreatmentPlan().getUpdatedAt())) {
            throw new ConflictException("Treatment plan changed; generate a new budget");
        }
        return budget;
    }

    private TreatmentBudgetItem snapshot(TreatmentPlanItem item) {
        BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new TreatmentBudgetItem(UUID.randomUUID(), item.getId(), item.getName(), item.getTooth(),
                item.getQuantity(), item.getUnitPrice(), subtotal, item.getPosition());
    }

    private User findActiveActor(UUID actorId) {
        requireId(actorId, "Authentication is required");
        User actor = users.findById(actorId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        if (actor.getStatus() != UserStatus.ACTIVE) throw new AccessDeniedException("Active user is required");
        return actor;
    }

    private void requireId(UUID id, String message) {
        if (id == null) throw new BadRequestException(message);
    }
}
