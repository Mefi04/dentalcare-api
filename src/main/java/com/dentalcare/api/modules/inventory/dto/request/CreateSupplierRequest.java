package com.dentalcare.api.modules.inventory.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(
        @NotBlank(message = "Supplier name is required")
        @Size(max = 150, message = "Supplier name must not exceed 150 characters")
        String name,

        @Size(max = 150, message = "Contact name must not exceed 150 characters")
        String contactName,

        @Size(max = 30, message = "Phone must not exceed 30 characters")
        String phone,

        @Email(message = "Email must be valid")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String email,

        @Size(max = 300, message = "Address must not exceed 300 characters")
        String address,

        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes
) {
}
