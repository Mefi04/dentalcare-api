package com.dentalcare.api.modules.billing.repository;

import com.dentalcare.api.modules.billing.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByPatient_IdOrderByCreatedAtAscIdAsc(UUID patientId);

    @Query("SELECT p FROM Payment p WHERE p.id = :id AND p.patient.id = :patientId")
    Optional<Payment> findByIdAndPatientId(@Param("id") UUID id, @Param("patientId") UUID patientId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.charge.id = :chargeId")
    BigDecimal sumAmountByChargeId(@Param("chargeId") UUID chargeId);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.method = com.dentalcare.api.modules.billing.model.PaymentMethod.CASH
              AND p.cashShift.id = :shiftId
            """)
    BigDecimal sumCashAmountByCashShiftId(@Param("shiftId") UUID shiftId);
}
