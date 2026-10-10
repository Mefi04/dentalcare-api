package com.dentalcare.api.modules.assistant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;

public record PublicAssistantRequest(
        @NotBlank @Size(max = 1000) String message,
        LocalDate availabilityDate,
        @AssertTrue boolean aiProcessingAccepted) { }
