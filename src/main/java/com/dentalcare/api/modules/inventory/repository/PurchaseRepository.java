package com.dentalcare.api.modules.inventory.repository;

import com.dentalcare.api.modules.inventory.model.Purchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID>, JpaSpecificationExecutor<Purchase> {

    @EntityGraph(attributePaths = {"supplier", "createdBy", "receivedBy", "items", "items.inventoryItem"})
    @Query("SELECT p FROM Purchase p WHERE p.id = :id")
    Optional<Purchase> findDetailById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT nextval('inventory_purchase_code_seq')", nativeQuery = true)
    Long getNextPurchaseCodeSeq();

    boolean existsByCode(String code);
}
