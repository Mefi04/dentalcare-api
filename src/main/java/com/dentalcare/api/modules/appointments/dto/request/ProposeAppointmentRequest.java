package com.dentalcare.api.modules.appointments.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ProposeAppointmentRequest(
        UUID professionalId,
        @NotNull @Future Instant proposedAt) {
}
