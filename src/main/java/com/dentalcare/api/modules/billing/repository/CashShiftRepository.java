package com.dentalcare.api.modules.billing.repository;

import com.dentalcare.api.modules.billing.model.CashShift;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashShiftRepository extends JpaRepository<CashShift, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT shift FROM CashShift shift
            WHERE shift.userId = :userId
              AND shift.status = com.dentalcare.api.modules.billing.model.CashShiftStatus.OPEN
            """)
    Optional<CashShift> findOpenByUserIdForUpdate(@Param("userId") UUID userId);

    @Query("""
            SELECT shift FROM CashShift shift
            WHERE shift.userId = :userId
              AND shift.status = com.dentalcare.api.modules.billing.model.CashShiftStatus.OPEN
            """)
    Optional<CashShift> findOpenByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT shift FROM CashShift shift WHERE shift.id = :id AND shift.userId = :userId")
    Optional<CashShift> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    Page<CashShift> findByUserId(UUID userId, Pageable pageable);
}
