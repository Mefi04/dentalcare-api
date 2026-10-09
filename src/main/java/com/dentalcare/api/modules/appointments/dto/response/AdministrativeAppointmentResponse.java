package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AdministrativeAppointmentResponse(
        UUID id,
        AdministrativeAppointmentPatientResponse patient,
        AppointmentProfessionalResponse professional,
        Instant scheduledAt,
        AppointmentStatus status,
        Instant createdAt,
        Instant updatedAt,
        PublicAppointmentRequesterResponse publicContact) {
    public AdministrativeAppointmentResponse(UUID id, AdministrativeAppointmentPatientResponse patient,
            AppointmentProfessionalResponse professional, Instant scheduledAt, AppointmentStatus status,
            Instant createdAt, Instant updatedAt) {
        this(id, patient, professional, scheduledAt, status, createdAt, updatedAt, null);
    }
}
