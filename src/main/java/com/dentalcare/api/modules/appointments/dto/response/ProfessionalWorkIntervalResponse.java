package com.dentalcare.api.modules.appointments.dto.response;

import java.time.LocalTime;
import java.util.UUID;

public record ProfessionalWorkIntervalResponse(UUID id, UUID professionalId, int dayOfWeek,
        LocalTime startTime, LocalTime endTime) { }
