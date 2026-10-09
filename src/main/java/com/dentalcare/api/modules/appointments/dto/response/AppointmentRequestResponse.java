package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public record AppointmentRequestResponse(
        UUID id,
        AdministrativeAppointmentPatientResponse patient,
        AppointmentProfessionalResponse requestedProfessional,
        Instant requestedAt,
        AppointmentProfessionalResponse proposedProfessional,
        Instant proposedAt,
        AppointmentRequestStatus status,
        String actionRequiredBy,
        UUID appointmentId,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) PublicAppointmentRequesterResponse publicRequester) {
}
