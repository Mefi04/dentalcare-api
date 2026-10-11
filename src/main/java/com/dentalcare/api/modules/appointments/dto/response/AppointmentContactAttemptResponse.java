package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.AppointmentContactResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Details of a contact attempt made by staff")
public record AppointmentContactAttemptResponse(
        UUID id,
        UUID requestId,
        AppointmentContactResult result,
        String notes,
        UUID createdBy,
        String createdByName,
        Instant createdAt
) {
}
