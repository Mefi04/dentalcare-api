package com.dentalcare.api.modules.appointments.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreatePatientAppointmentRequest(
        @NotNull(message = "Professional id is required") UUID professionalId,
        @NotNull(message = "Appointment date and time are required") Instant scheduledAt) {
}
