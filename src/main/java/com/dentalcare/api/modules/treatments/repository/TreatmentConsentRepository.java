package com.dentalcare.api.modules.treatments.repository;

import com.dentalcare.api.modules.treatments.model.TreatmentConsent;
import com.dentalcare.api.modules.treatments.model.TreatmentConsentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentConsentRepository extends JpaRepository<TreatmentConsent, UUID> {
    @EntityGraph(attributePaths = {"treatmentPlan", "patient", "treatmentBudget", "preparedBy", "acceptedBy", "revokedBy"})
    List<TreatmentConsent> findByTreatmentPlan_IdOrderByCreatedAtDesc(UUID planId);

    @EntityGraph(attributePaths = {"treatmentPlan", "patient", "treatmentBudget", "preparedBy", "acceptedBy", "revokedBy"})
    @Query("SELECT consent FROM TreatmentConsent consent WHERE consent.id = :id")
    Optional<TreatmentConsent> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"treatmentPlan", "patient", "treatmentBudget", "preparedBy", "acceptedBy", "revokedBy"})
    @Query("SELECT consent FROM TreatmentConsent consent WHERE consent.id = :id")
    Optional<TreatmentConsent> findDetailedByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"treatmentPlan", "patient", "treatmentBudget", "preparedBy", "acceptedBy", "revokedBy"})
    @Query("SELECT consent FROM TreatmentConsent consent WHERE consent.treatmentPlan.id = :planId "
            + "AND consent.status IN :statuses")
    Optional<TreatmentConsent> findActiveByPlanIdForUpdate(
            @Param("planId") UUID planId,
            @Param("statuses") List<TreatmentConsentStatus> statuses);
}
