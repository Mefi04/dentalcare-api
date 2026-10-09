package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

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
        @JsonInclude(JsonInclude.Include.NON_NULL) PublicAppointmentRequesterResponse publicRequester,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Administrative source classification: PUBLIC or PATIENT_PORTAL") String source,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Non-clinical contact details for public submissions") PublicAppointmentRequesterResponse contact,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "Reception assignment; null when no dentist has been assigned")
        AppointmentProfessionalResponse assignedProfessional,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant proposalExpiresAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<AppointmentRequestMessageResponse> messages,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Administrative identity verification record for a public requester")
        PublicRequesterIdentityVerificationResponse identityVerification) {
}
