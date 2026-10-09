package com.dentalcare.api.modules.appointments.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PublicRequesterIdentityVerificationResponse(
        Instant verifiedAt,
        UUID verifiedByUserId,
        String method) {
}
