package com.dentalcare.api.modules.appointments.dto.request;

import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAppointmentStatusRequest(@NotNull AppointmentStatus status) {
}
