package com.dentalcare.api.modules.appointments.dto.response;

import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import java.time.Instant;
import java.util.UUID;

public record ReceptionFirstAppointmentItem(UUID id, String fullName, String phone,
        Instant preferredAt, AppointmentRequestStatus status, boolean conflict,
        Instant createdAt) { }
