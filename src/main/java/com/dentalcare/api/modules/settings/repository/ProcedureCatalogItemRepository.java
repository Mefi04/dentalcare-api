package com.dentalcare.api.modules.settings.repository;

import com.dentalcare.api.modules.settings.model.ProcedureCatalogItem;
import com.dentalcare.api.modules.settings.model.ProcedureCatalogItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProcedureCatalogItemRepository extends JpaRepository<ProcedureCatalogItem, UUID>,
        JpaSpecificationExecutor<ProcedureCatalogItem> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    List<ProcedureCatalogItem> findByStatusOrderByNameAscIdAsc(ProcedureCatalogItemStatus status);
}
