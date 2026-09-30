package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.model.Appointment;

import java.time.Instant;
import java.util.UUID;

public interface AppointmentService {
    Appointment create(UUID patientId, UUID professionalId, Instant scheduledAt);
    Appointment findById(UUID id);
    Appointment cancel(Appointment appointment);
}
