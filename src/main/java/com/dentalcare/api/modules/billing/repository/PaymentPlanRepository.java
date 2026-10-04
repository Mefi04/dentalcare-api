package com.dentalcare.api.modules.billing.repository;

import com.dentalcare.api.modules.billing.model.PaymentPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, UUID> {

    @Query("SELECT plan FROM PaymentPlan plan WHERE plan.id = :id AND plan.patientId = :patientId")
    Optional<PaymentPlan> findByIdAndPatientId(@Param("id") UUID id, @Param("patientId") UUID patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT plan FROM PaymentPlan plan WHERE plan.id = :id AND plan.patientId = :patientId")
    Optional<PaymentPlan> findByIdAndPatientIdForUpdate(@Param("id") UUID id, @Param("patientId") UUID patientId);

    @Query("""
            SELECT plan FROM PaymentPlan plan
            WHERE plan.chargeId = :chargeId
              AND plan.patientId = :patientId
              AND plan.status = com.dentalcare.api.modules.billing.model.PaymentPlanStatus.ACTIVE
            """)
    Optional<PaymentPlan> findActiveByChargeIdAndPatientId(@Param("chargeId") UUID chargeId,
                                                            @Param("patientId") UUID patientId);

    List<PaymentPlan> findByPatientIdOrderByCreatedAtDescIdDesc(UUID patientId);
}
