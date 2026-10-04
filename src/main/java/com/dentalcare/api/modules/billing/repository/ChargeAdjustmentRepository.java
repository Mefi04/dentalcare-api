package com.dentalcare.api.modules.billing.repository;

import com.dentalcare.api.modules.billing.model.ChargeAdjustment;
import com.dentalcare.api.modules.billing.model.ChargeAdjustmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChargeAdjustmentRepository extends JpaRepository<ChargeAdjustment, UUID> {

    @Query("""
            SELECT COALESCE(SUM(a.amount), 0) FROM ChargeAdjustment a
            WHERE a.chargeId = :chargeId
              AND a.type = com.dentalcare.api.modules.billing.model.ChargeAdjustmentType.DISCOUNT
            """)
    BigDecimal sumDiscountByChargeId(@Param("chargeId") UUID chargeId);

    boolean existsByChargeIdAndType(UUID chargeId, ChargeAdjustmentType type);

    List<ChargeAdjustment> findByPatientIdOrderByCreatedAtAscIdAsc(UUID patientId);
}
