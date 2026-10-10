package com.dentalcare.api.modules.appointments.dto.response;

import java.time.LocalDate;
import java.util.List;

public record PublicAppointmentAvailabilityResponse(LocalDate date, String timeZone,
        int durationMinutes, List<PublicAppointmentSlotResponse> slots) { }
