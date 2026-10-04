package com.dentalcare.api.modules.settings.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateClinicSettingsRequest(
        @NotBlank @Size(max=150) String tradeName,
        @NotBlank @Size(max=20) @Pattern(regexp="(?i)^[0-9]{1,15}-?[0-9K]$", message="NIT format is invalid") String nit,
        @NotBlank @Size(max=30) @Pattern(regexp="^[0-9+() .-]{7,30}$", message="Phone format is invalid") String phone,
        @Email @Size(max=255) String email,
        @Size(max=255) String address,
        @Size(max=100) String city,
        @Size(max=255) String businessHours,
        @Size(max=20) @Pattern(regexp="^[A-Za-z0-9_-]*$", message="Receipt prefix format is invalid") String receiptPrefix) {}
