package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.WaitingRoomStatus;

import java.time.Instant;
import java.util.UUID;

public record WaitingRoomEntryResponse(
        UUID id,
        UUID appointmentId,
        AdministrativeAppointmentPatientResponse patient,
        AppointmentProfessionalResponse professional,
        Instant scheduledAt,
        WaitingRoomStatus status,
        Instant arrivedAt,
        Instant waitingAt,
        Instant readyAt,
        Instant closedAt,
        UUID checkedInBy,
        UUID lastUpdatedBy,
        Instant updatedAt) {
}
