package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.request.CreateAdministrativeAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.UUID;

public interface AdministrativeAppointmentService {

    Page<AdministrativeAppointmentResponse> findAll(
            Instant from, Instant to, UUID patientId, UUID professionalId,
            AppointmentStatus status, int page, int size);

    AdministrativeAppointmentResponse findById(UUID appointmentId);

    AdministrativeAppointmentResponse create(CreateAdministrativeAppointmentRequest request);

    AdministrativeAppointmentResponse reschedule(UUID appointmentId, Instant scheduledAt);

    AdministrativeAppointmentResponse updateStatus(UUID actorId, UUID appointmentId, AppointmentStatus status);
}
