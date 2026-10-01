package com.dentalcare.api.modules.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreatePurchaseRequest(
        @Size(max = 50, message = "Code must not exceed 50 characters")
        String code,

        @NotNull(message = "Supplier id is required")
        UUID supplierId,

        @NotNull(message = "Purchase date is required")
        LocalDate purchaseDate,

        @Size(max = 100, message = "Reference must not exceed 100 characters")
        String reference,

        @Size(max = 500, message = "Observation must not exceed 500 characters")
        String observation,

        @NotEmpty(message = "Purchase items cannot be empty")
        @Valid
        List<CreatePurchaseItemRequest> items
) {
}
