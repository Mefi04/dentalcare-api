package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.AppointmentNotificationStatus;
import java.time.Instant;
import java.util.UUID;

public record AppointmentNotificationOutboxResponse(UUID id, UUID requestId, String type, String channel,
        AppointmentNotificationStatus status, int attempts, Instant createdAt, Instant nextAttemptAt,
        Instant lastAttemptAt, Instant sentAt, String errorCode, UUID correlationId) {}
