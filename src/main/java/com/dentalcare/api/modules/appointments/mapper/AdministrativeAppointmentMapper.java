package com.dentalcare.api.modules.appointments.mapper;

import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentPatientResponse;
import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentProfessionalResponse;
import com.dentalcare.api.modules.appointments.model.Appointment;
import org.springframework.stereotype.Component;

@Component
public class AdministrativeAppointmentMapper {

    public AdministrativeAppointmentResponse toResponse(Appointment appointment) {
        AdministrativeAppointmentPatientResponse patientResponse = appointment.getPatient() != null
                ? new AdministrativeAppointmentPatientResponse(
                        appointment.getPatient().getId(),
                        appointment.getPatient().getCode(),
                        appointment.getPatient().getName(),
                        appointment.getPatient().getPhone())
                : new AdministrativeAppointmentPatientResponse(
                        null,
                        "EXTERNO",
                        appointment.getPublicContactName(),
                        appointment.getPublicContactPhone());
        return new AdministrativeAppointmentResponse(
                appointment.getId(),
                patientResponse,
                new AppointmentProfessionalResponse(
                        appointment.getProfessional().getId(),
                        appointment.getProfessional().getFullName()),
                appointment.getScheduledAt(),
                appointment.getStatus(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt());
    }
}
