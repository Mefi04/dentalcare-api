package com.dentalcare.api.modules.appointments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Aggregated appointment availability response for a given date")
public record AppointmentAvailabilityResponse(
        @Schema(description = "Requested clinic date in YYYY-MM-DD", example = "2026-10-20")
        LocalDate date,

        @Schema(description = "Service code or specialty queried", example = "GENERAL_DENTISTRY")
        String serviceCode,

        @Schema(description = "Dentist ID if specific filter was requested, null for aggregate clinic availability")
        UUID professionalId,

        @Schema(description = "Available time slots on the requested date")
        List<AppointmentSlotResponse> slots
) {
}
