package com.dentalcare.api.modules.appointments.mapper;

import com.dentalcare.api.modules.appointments.dto.response.AppointmentProfessionalResponse;
import com.dentalcare.api.modules.appointments.dto.response.PatientAppointmentResponse;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMapper {

    public PatientAppointmentResponse toPatientResponse(Appointment appointment) {
        User professional = appointment.getProfessional();
        return new PatientAppointmentResponse(
                appointment.getId(),
                appointment.getScheduledAt(),
                appointment.getStatus(),
                new AppointmentProfessionalResponse(professional.getId(), professional.getFullName()));
    }
}
