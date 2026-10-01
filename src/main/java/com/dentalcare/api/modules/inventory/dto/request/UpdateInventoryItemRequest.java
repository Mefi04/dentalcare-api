package com.dentalcare.api.modules.inventory.dto.request;

import com.dentalcare.api.modules.inventory.model.InventoryItemStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateInventoryItemRequest(
        @Size(max = 50, message = "Code must not exceed 50 characters")
        String code,

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @NotBlank(message = "Category is required")
        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        InventoryItemStatus status,

        @Size(max = 50, message = "Unit must not exceed 50 characters")
        String unit,

        @Min(value = 0, message = "Minimum stock must not be negative")
        Integer minimumStock,

        LocalDate expirationDate,

        @Size(max = 100, message = "Location must not exceed 100 characters")
        String location
) {
}
