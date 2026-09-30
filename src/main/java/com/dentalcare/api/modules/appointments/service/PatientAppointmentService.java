package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.response.AppointmentProfessionalResponse;
import com.dentalcare.api.modules.appointments.dto.response.PatientAppointmentResponse;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PatientAppointmentService {

    Page<PatientAppointmentResponse> findCurrentPatientAppointments(UUID authenticatedUserId, int page, int size);

    PatientAppointmentResponse findCurrentPatientAppointment(UUID authenticatedUserId, UUID appointmentId);

    PatientAppointmentResponse createCurrentPatientAppointment(
            UUID authenticatedUserId, UUID professionalId, Instant scheduledAt);

    PatientAppointmentResponse cancelCurrentPatientAppointment(
            UUID authenticatedUserId, UUID appointmentId);

    List<AppointmentProfessionalResponse> findAvailableProfessionals();
}
