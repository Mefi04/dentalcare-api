package com.dentalcare.api.modules.patients.dto.request;

import com.dentalcare.api.modules.patients.model.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record UpdatePatientRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "DPI is required")
        @Pattern(regexp = "[0-9 ]+", message = "DPI must contain only digits and spaces") String dpi,
        @NotNull(message = "Birth date is required")
        @PastOrPresent(message = "Birth date cannot be in the future") LocalDate birthDate,
        @NotNull(message = "Gender is required") Gender gender,
        @NotBlank(message = "Phone is required") String phone,
        @Email(message = "Email must be valid") String email,
        String city,
        String address,
        String emergencyContact,
        String emergencyPhone,
        String billingName,
        String nit,
        String billingAddress,
        String guardianName,
        String guardianRelationship,
        String guardianPhone) {
}
