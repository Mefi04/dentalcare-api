package com.dentalcare.api.modules.billing.repository;

import com.dentalcare.api.modules.billing.model.CashMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    Page<CashMovement> findByCashShift_Id(UUID cashShiftId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(movement.amount), 0) FROM CashMovement movement
            WHERE movement.cashShift.id = :shiftId
              AND movement.type = com.dentalcare.api.modules.billing.model.CashMovementType.INCOME
            """)
    BigDecimal sumIncomeByCashShiftId(@Param("shiftId") UUID shiftId);

    @Query("""
            SELECT COALESCE(SUM(movement.amount), 0) FROM CashMovement movement
            WHERE movement.cashShift.id = :shiftId
              AND movement.type = com.dentalcare.api.modules.billing.model.CashMovementType.EXPENSE
            """)
    BigDecimal sumExpenseByCashShiftId(@Param("shiftId") UUID shiftId);
}
