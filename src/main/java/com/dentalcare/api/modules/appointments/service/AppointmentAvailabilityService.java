package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.response.AppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentSlotResponse;
import com.dentalcare.api.modules.users.model.ProfessionalServiceCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AppointmentAvailabilityService {

    AppointmentAvailabilityResponse getPublicAvailability(LocalDate date, UUID professionalId, ProfessionalServiceCode serviceCode);

    AppointmentAvailabilityResponse getPatientAvailability(LocalDate date, UUID professionalId, ProfessionalServiceCode serviceCode);

    AppointmentAvailabilityResponse getAdministrativeAvailability(LocalDate date, UUID professionalId, ProfessionalServiceCode serviceCode);

    List<AppointmentSlotResponse> getAvailableSlotsForGemini(LocalDate date);

    boolean isSlotAvailableForBooking(Instant scheduledAt, UUID professionalId);
}
