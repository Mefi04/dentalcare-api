package com.dentalcare.api.modules.treatments.repository;

import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TreatmentProcedureRepository extends JpaRepository<TreatmentProcedure, UUID> {

    @EntityGraph(attributePaths = {"treatmentPlan", "treatmentPlanItem", "patient", "professional"})
    Page<TreatmentProcedure> findByTreatmentPlan_Id(UUID treatmentPlanId, Pageable pageable);

    @EntityGraph(attributePaths = {"treatmentPlan", "treatmentPlanItem", "patient", "professional"})
    Page<TreatmentProcedure> findByPatient_Id(UUID patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"treatmentPlan", "treatmentPlanItem", "professional"})
    @Query("SELECT procedure FROM TreatmentProcedure procedure " +
            "WHERE procedure.treatmentPlan.id IN :planIds ORDER BY procedure.performedAt ASC, procedure.id ASC")
    List<TreatmentProcedure> findByTreatmentPlanIds(@Param("planIds") Collection<UUID> planIds);

    @EntityGraph(attributePaths = {"treatmentPlan", "treatmentPlanItem", "professional"})
    @Query("SELECT procedure FROM TreatmentProcedure procedure " +
            "WHERE procedure.treatmentPlan.id = :planId AND procedure.patient.id = :patientId " +
            "ORDER BY procedure.performedAt ASC, procedure.id ASC")
    List<TreatmentProcedure> findOwnedByPlanId(@Param("planId") UUID planId,
                                               @Param("patientId") UUID patientId);

    @EntityGraph(attributePaths = {"treatmentPlan", "treatmentPlanItem", "patient", "professional"})
    @Query("SELECT procedure FROM TreatmentProcedure procedure WHERE procedure.id = :id")
    Optional<TreatmentProcedure> findDetailedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"treatmentPlan", "treatmentPlanItem", "patient", "professional"})
    @Query("SELECT procedure FROM TreatmentProcedure procedure WHERE procedure.id = :id")
    Optional<TreatmentProcedure> findDetailedByIdForUpdate(@Param("id") UUID id);

    boolean existsByTreatmentPlanItem_IdAndStatus(UUID treatmentPlanItemId, TreatmentProcedureStatus status);

    long countByTreatmentPlanItem_IdAndStatus(UUID treatmentPlanItemId, TreatmentProcedureStatus status);
}
