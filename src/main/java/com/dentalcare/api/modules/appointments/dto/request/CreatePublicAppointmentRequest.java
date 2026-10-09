package com.dentalcare.api.modules.appointments.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Non-clinical information needed to request a first appointment")
public record CreatePublicAppointmentRequest(
        @NotBlank @Size(max = 150)
        @Schema(example = "María López")
        String fullName,

        @Pattern(regexp = "^[0-9]{13}$", message = "CUI must contain exactly 13 digits")
        @Schema(description = "Optional Guatemalan DPI/CUI; never authenticates the visitor or auto-links an expediente")
        String cui,

        @NotBlank @Size(max = 30)
        @Pattern(regexp = "^[+0-9() .-]{7,30}$", message = "Phone number format is invalid")
        @Schema(example = "+502 5555-0101")
        String phone,

        @Email @Size(max = 255)
        @Schema(example = "maria@example.com")
        String email,

        @NotNull @Future
        @Schema(description = "Preferred future time in ISO-8601 UTC. This is a request, not a reservation.",
                example = "2027-03-15T16:00:00Z")
        Instant requestedAt,

        @Schema(description = "Optional preferred active dentist; omit or send null if there is no preference")
        UUID professionalId,

        @Size(max = 300)
        @Schema(description = "Optional non-clinical scheduling context only. Do not include symptoms or medical details.",
                example = "Primera consulta, horario de tarde")
        String reason) {
}
