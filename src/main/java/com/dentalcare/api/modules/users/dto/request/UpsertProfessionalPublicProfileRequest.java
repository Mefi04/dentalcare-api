package com.dentalcare.api.modules.users.dto.request;

import jakarta.validation.constraints.*;

public record UpsertProfessionalPublicProfileRequest(
        @NotBlank @Size(max = 100) String professionalRegistration,
        @NotBlank @Size(max = 150) String specialty,
        @NotBlank @Size(max = 1000) String summary,
        @Min(0) @Max(80) Integer yearsExperience,
        @Size(max = 255) String languages,
        @Size(max = 2048) @Pattern(regexp = "(?i)^https?://\\S+$", message = "Photo URL must use HTTP or HTTPS") String photoUrl,
        @NotNull Boolean publicVisible) { }
