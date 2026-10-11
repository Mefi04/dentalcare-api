package com.dentalcare.api.modules.appointments.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Non-clinical administrative information needed to request a first appointment")
public record CreatePublicAppointmentRequest(
        @NotBlank @Size(max = 150)
        @Schema(example = "María López")
        String fullName,

        @Pattern(regexp = "^[0-9]{13}$", message = "CUI must contain exactly 13 digits")
        @Schema(description = "Optional Guatemalan DPI/CUI for identity reference (verified in person)")
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
        String reason,

        @Schema(description = "Date of birth (YYYY-MM-DD)")
        LocalDate birthDate,

        @Size(max = 20)
        @Schema(description = "Gender")
        String gender,

        @Size(max = 50)
        @Schema(description = "Alternative ID or passport for foreigners")
        String alternativeId,

        @Size(max = 150)
        @Schema(description = "Legal guardian full name if patient is a minor")
        String guardianName,

        @Size(max = 50)
        @Schema(description = "Relationship with guardian (e.g. Madre, Padre, Tutor)")
        String guardianRelationship,

        @Size(max = 30)
        @Schema(description = "Guardian contact phone")
        String guardianPhone,

        @Size(max = 100)
        @Schema(description = "Guatemalan department")
        String department,

        @Size(max = 100)
        @Schema(description = "Guatemalan municipality")
        String municipality,

        @Size(max = 255)
        @Schema(description = "Residential address")
        String address,

        @Size(max = 150)
        @Schema(description = "Emergency contact name")
        String emergencyName,

        @Size(max = 30)
        @Schema(description = "Emergency contact phone")
        String emergencyPhone,

        @Size(max = 20)
        @Schema(description = "NIT for electronic invoice")
        String nit,

        @Size(max = 150)
        @Schema(description = "Billing business or person name")
        String billingName,

        @Size(max = 255)
        @Schema(description = "Billing address")
        String billingAddress,

        @AssertTrue(message = "Privacy notice must be accepted")
        @Schema(description = "Mandatory acceptance of privacy notice", example = "true")
        Boolean privacyAccepted,

        @Size(max = 20)
        @Schema(description = "Accepted privacy notice version", example = "1.0")
        String privacyNoticeVersion
) {
    public CreatePublicAppointmentRequest(
            String fullName,
            String cui,
            String phone,
            String email,
            Instant requestedAt,
            UUID professionalId,
            String reason) {
        this(fullName, cui, phone, email, requestedAt, professionalId, reason,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                true, "1.0");
    }
}
