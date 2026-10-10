package com.dentalcare.api.modules.appointments.dto.response;

import java.time.Instant;
import java.util.List;

public record FirstAppointmentSchedulingOptionsResponse(Instant preferredAt,
        String preferredStatus, boolean conflict, List<PublicAppointmentSlotResponse> alternatives) { }
