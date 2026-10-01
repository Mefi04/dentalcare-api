package com.dentalcare.api.modules.inventory.dto.request;

import com.dentalcare.api.modules.inventory.model.SupplierStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSupplierStatusRequest(
        @NotNull(message = "Supplier status is required")
        SupplierStatus status
) {
}
