package com.dentalcare.api.modules.inventory.repository;

import com.dentalcare.api.modules.inventory.model.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID>,
        JpaSpecificationExecutor<InventoryMovement> {

    @Override
    @EntityGraph(attributePaths = {"item", "performedBy"})
    Page<InventoryMovement> findAll(Specification<InventoryMovement> specification, Pageable pageable);
}
