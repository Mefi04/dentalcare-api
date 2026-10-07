package com.dentalcare.api.modules.settings.mapper;

import com.dentalcare.api.modules.settings.dto.response.ProcedureCatalogItemResponse;
import com.dentalcare.api.modules.settings.model.ProcedureCatalogItem;
import org.springframework.stereotype.Component;

@Component
public class ProcedureCatalogItemMapper {
    public ProcedureCatalogItemResponse toResponse(ProcedureCatalogItem item) {
        return new ProcedureCatalogItemResponse(item.getId(), item.getCode(), item.getName(), item.getCategory(),
                item.getDescription(), item.getDurationMinutes(), item.getBasePrice(), item.getStatus(),
                item.getCreatedBy(), item.getUpdatedBy(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
