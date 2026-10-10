package com.dentalcare.api.modules.appointments.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalTime;

public record CreateProfessionalWorkIntervalRequest(@Min(1) @Max(7) int dayOfWeek,
        @NotNull LocalTime startTime, @NotNull LocalTime endTime) { }
