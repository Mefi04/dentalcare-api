package com.dentalcare.api.modules.settings.dto.request;

import com.dentalcare.api.modules.settings.model.ProcedureCatalogItemStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProcedureCatalogItemStatusRequest(@NotNull ProcedureCatalogItemStatus status) {}
