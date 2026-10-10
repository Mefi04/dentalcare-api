package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.AppointmentContactResult;
import java.time.Instant;
import java.util.UUID;

public record AppointmentContactAttemptResponse(UUID id, UUID requestId, UUID actorId,
        Instant attemptedAt, AppointmentContactResult result, String observation,
        Instant nextAttemptAt) { }
