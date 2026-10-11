package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.SlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Representation of an appointment time slot with its availability status")
public record AppointmentSlotResponse(
        @Schema(description = "Instant in UTC when the slot starts", example = "2026-10-20T14:00:00Z")
        Instant slotTime,

        @Schema(description = "Local time in America/Guatemala", example = "08:00")
        String localTime,

        @Schema(description = "Availability status for this slot: AVAILABLE, REQUESTED, BOOKED, UNAVAILABLE")
        SlotStatus status,

        @Schema(description = "Number of open capacity spots currently available", example = "2")
        int availableCapacity,

        @Schema(description = "Total theoretical capacity for this slot", example = "2")
        int totalCapacity
) {
}
