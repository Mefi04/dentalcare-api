package com.dentalcare.api.modules.appointments.dto.response;
import java.time.Instant;
import java.util.UUID;
public record AppointmentRequestMessageResponse(UUID id, String sender, String type, String text, Instant createdAt) {}
