package com.dentalcare.api.modules.appointments.mapper;

import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentPatientResponse;
import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentProfessionalResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentRequesterResponse;
import com.dentalcare.api.modules.appointments.model.Appointment;
import org.springframework.stereotype.Component;

@Component
public class AdministrativeAppointmentMapper {

    public AdministrativeAppointmentResponse toResponse(Appointment appointment) {
        return new AdministrativeAppointmentResponse(
                appointment.getId(),
                appointment.getPatient() == null ? null : new AdministrativeAppointmentPatientResponse(
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
                appointment.getUpdatedAt(),
                appointment.getPublicContactName() == null ? null : new PublicAppointmentRequesterResponse(
                        appointment.getPublicContactName(), null, appointment.getPublicContactPhone(), null, null));
    }
}
