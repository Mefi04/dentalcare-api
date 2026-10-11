package com.dentalcare.api.modules.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Message sent to the public virtual assistant")
public record PublicAssistantRequest(
        @NotBlank @Size(max = 1000)
        @Schema(description = "User query or question", example = "¿Qué horarios tienen disponibles para primera cita?")
        String message,

        @Schema(description = "Optional specific date for checking appointment availability (YYYY-MM-DD)", example = "2026-10-20")
        LocalDate availabilityDate,

        @NotNull
        @AssertTrue(message = "Explicit consent for AI processing is required (aiProcessingAccepted must be true)")
        @Schema(description = "Mandatory user consent for AI message processing", example = "true")
        Boolean aiProcessingAccepted
) {
}
