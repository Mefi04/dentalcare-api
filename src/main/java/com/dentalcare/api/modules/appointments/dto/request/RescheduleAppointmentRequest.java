package com.dentalcare.api.modules.appointments.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleAppointmentRequest(@NotNull @Future Instant scheduledAt) {
}
