package com.dentalcare.api.modules.appointments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Safe acknowledgment for an anonymous appointment request")
public record PublicAppointmentRequestReceipt(
        UUID requestId,
        String message) {
}
