package com.dentalcare.api.modules.billing.repository;

import com.dentalcare.api.modules.billing.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.paymentId = :paymentId")
    BigDecimal sumAmountByPaymentId(@Param("paymentId") UUID paymentId);

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM Refund r
            WHERE r.paymentId IN (SELECT p.id FROM Payment p WHERE p.charge.id = :chargeId)
            """)
    BigDecimal sumAmountByChargeId(@Param("chargeId") UUID chargeId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.cashShiftId = :shiftId")
    BigDecimal sumAmountByCashShiftId(@Param("shiftId") UUID shiftId);

    Optional<Refund> findByRequestedByUserIdAndIdempotencyKey(UUID requestedByUserId, String idempotencyKey);

    List<Refund> findByPatientIdOrderByCreatedAtAscIdAsc(UUID patientId);
}
