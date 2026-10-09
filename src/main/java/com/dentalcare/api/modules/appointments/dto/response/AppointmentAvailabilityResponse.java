package com.dentalcare.api.modules.appointments.dto.response;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record AppointmentAvailabilityResponse(UUID professionalId, LocalDate date, String timezone,
                                               List<Instant> bookedTimes) {}
