package com.dentalcare.api.modules.inventory.dto.response;

import com.dentalcare.api.modules.inventory.model.PurchaseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        String code,
        UUID supplierId,
        String supplierName,
        PurchaseStatus status,
        LocalDate purchaseDate,
        String reference,
        String observation,
        BigDecimal totalAmount,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        UUID receivedBy,
        String receivedByName,
        Instant receivedAt,
        List<PurchaseItemResponse> items
) {
}
