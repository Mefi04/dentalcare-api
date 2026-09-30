package com.dentalcare.api.modules.appointments.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateAdministrativeAppointmentRequest(
        @NotNull UUID patientId,
        @NotNull UUID professionalId,
        @NotNull @Future Instant scheduledAt) {
}
