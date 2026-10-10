package com.dentalcare.api.modules.appointments.dto.request;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateProfessionalScheduleBlockRequest(@NotNull Instant startsAt,
        @NotNull Instant endsAt, @Size(max = 150) String reason) { }
