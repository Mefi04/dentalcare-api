package com.dentalcare.api.modules.treatments.repository;

import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, UUID> {

    Page<TreatmentPlan> findByPatient_Id(UUID patientId, Pageable pageable);

    @Query("SELECT tp.id FROM TreatmentPlan tp WHERE tp.patient.id = :patientId " +
            "AND tp.status = com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus.APPROVED")
    Page<UUID> findApprovedIdsByPatientId(@Param("patientId") UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "professional", "items"})
    @Query("SELECT DISTINCT tp FROM TreatmentPlan tp WHERE tp.id IN :ids")
    List<TreatmentPlan> findDetailedByIdIn(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = {"patient", "professional", "items"})
    @Query("SELECT tp FROM TreatmentPlan tp WHERE tp.id = :id")
    Optional<TreatmentPlan> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"patient", "professional", "items"})
    @Query("SELECT DISTINCT tp FROM TreatmentPlan tp WHERE tp.id = :id")
    Optional<TreatmentPlan> findDetailedByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TreatmentPlanItem item WHERE item.treatmentPlan.id = :treatmentPlanId")
    void deleteItemsByTreatmentPlanId(@Param("treatmentPlanId") UUID treatmentPlanId);
}
