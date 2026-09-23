package com.dentalcare.api.modules.users.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStaffUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters") String fullName,
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters") String email,
        @NotBlank(message = "Role code is required") String roleCode) {
}
