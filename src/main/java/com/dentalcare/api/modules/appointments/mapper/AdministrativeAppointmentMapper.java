package com.dentalcare.api.modules.appointments.mapper;

import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentPatientResponse;
import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentProfessionalResponse;
import com.dentalcare.api.modules.appointments.model.Appointment;
import org.springframework.stereotype.Component;

@Component
public class AdministrativeAppointmentMapper {

    public AdministrativeAppointmentResponse toResponse(Appointment appointment) {
        return new AdministrativeAppointmentResponse(
                appointment.getId(),
                new AdministrativeAppointmentPatientResponse(
                        appointment.getPatient().getId(),
                        appointment.getPatient().getCode(),
                        appointment.getPatient().getName(),
                        appointment.getPatient().getPhone()),
                new AppointmentProfessionalResponse(
                        appointment.getProfessional().getId(),
                        appointment.getProfessional().getFullName()),
                appointment.getScheduledAt(),
                appointment.getStatus(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt());
    }
}
