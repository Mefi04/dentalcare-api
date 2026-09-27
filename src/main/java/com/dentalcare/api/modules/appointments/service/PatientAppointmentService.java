package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.response.PatientAppointmentResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PatientAppointmentService {

    Page<PatientAppointmentResponse> findCurrentPatientAppointments(UUID authenticatedUserId, int page, int size);

    PatientAppointmentResponse findCurrentPatientAppointment(UUID authenticatedUserId, UUID appointmentId);
}
