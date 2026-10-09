package com.dentalcare.api.modules.appointments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.time.Instant;

@Schema(description = "Safe acknowledgment for an anonymous appointment request")
public record PublicAppointmentRequestReceipt(
        UUID requestId,
        String message,
        @Schema(description = "One-time 256-bit bearer secret scoped to this request. Save securely; it is never stored in plaintext.")
        String conversationToken,
        @Schema(example = "Bearer")
        String tokenType,
        @Schema(description = "Conversation-token expiry; an idempotent retry with the same key and payload issues a replacement token.")
        Instant conversationExpiresAt) {

    public PublicAppointmentRequestReceipt(UUID requestId, String message) {
        this(requestId, message, null, null, null);
    }
}
