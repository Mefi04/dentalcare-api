package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record PatientAppointmentResponse(
        UUID id,
        Instant scheduledAt,
        AppointmentStatus status,
        AppointmentProfessionalResponse professional) {
}
