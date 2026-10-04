package com.dentalcare.api.modules.treatments.repository;

import com.dentalcare.api.modules.treatments.model.TreatmentBudget;
import com.dentalcare.api.modules.treatments.model.TreatmentBudgetStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentBudgetRepository extends JpaRepository<TreatmentBudget, UUID> {
    @EntityGraph(attributePaths = {"treatmentPlan", "patient", "generatedBy", "decidedBy", "items"})
    List<TreatmentBudget> findByTreatmentPlan_IdOrderByVersionDesc(UUID planId);

    @EntityGraph(attributePaths = {"treatmentPlan", "patient", "generatedBy", "decidedBy", "items"})
    @Query("SELECT DISTINCT budget FROM TreatmentBudget budget WHERE budget.id = :id")
    Optional<TreatmentBudget> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"treatmentPlan", "patient", "generatedBy", "decidedBy", "items"})
    @Query("SELECT DISTINCT budget FROM TreatmentBudget budget WHERE budget.id = :id")
    Optional<TreatmentBudget> findDetailedByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"treatmentPlan", "patient", "generatedBy", "decidedBy", "items"})
    @Query("SELECT DISTINCT budget FROM TreatmentBudget budget WHERE budget.treatmentPlan.id = :planId "
            + "AND budget.status IN :statuses")
    Optional<TreatmentBudget> findActiveByPlanIdForUpdate(
            @Param("planId") UUID planId,
            @Param("statuses") List<TreatmentBudgetStatus> statuses);

    Optional<TreatmentBudget> findFirstByTreatmentPlan_IdAndStatusOrderByVersionDesc(
            UUID planId, TreatmentBudgetStatus status);

    @Query("SELECT COALESCE(MAX(budget.version), 0) FROM TreatmentBudget budget "
            + "WHERE budget.treatmentPlan.id = :planId")
    int findMaxVersionByPlanId(@Param("planId") UUID planId);
}
