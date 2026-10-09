package com.dentalcare.api.modules.appointments.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignAppointmentRequestProfessionalRequest(@NotNull UUID professionalId) {
}
