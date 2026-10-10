package com.dentalcare.api.modules.appointments.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ProfessionalScheduleBlockResponse(UUID id, UUID professionalId, Instant startsAt,
        Instant endsAt, String reason) { }
