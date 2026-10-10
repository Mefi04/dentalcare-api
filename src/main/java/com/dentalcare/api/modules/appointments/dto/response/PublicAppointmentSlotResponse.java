package com.dentalcare.api.modules.appointments.dto.response;

import java.time.Instant;

public record PublicAppointmentSlotResponse(Instant startsAt, String status,
        int availableProfessionals, int pendingRequests) { }
