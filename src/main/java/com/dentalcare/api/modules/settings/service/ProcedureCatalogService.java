package com.dentalcare.api.modules.settings.service;

import com.dentalcare.api.modules.settings.dto.request.CreateProcedureCatalogItemRequest;
import com.dentalcare.api.modules.settings.dto.request.UpdateProcedureCatalogItemRequest;
import com.dentalcare.api.modules.settings.dto.response.ProcedureCatalogItemResponse;
import com.dentalcare.api.modules.settings.model.ProcedureCatalogItemStatus;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.UUID;

public interface ProcedureCatalogService {
    Page<ProcedureCatalogItemResponse> findAll(String search, String category, ProcedureCatalogItemStatus status, int page, int size);
    List<ProcedureCatalogItemResponse> findActive();
    ProcedureCatalogItemResponse findById(UUID id);
    ProcedureCatalogItemResponse create(CreateProcedureCatalogItemRequest request, UUID actorId);
    ProcedureCatalogItemResponse update(UUID id, UpdateProcedureCatalogItemRequest request, UUID actorId);
    ProcedureCatalogItemResponse updateStatus(UUID id, ProcedureCatalogItemStatus status, UUID actorId);
}
