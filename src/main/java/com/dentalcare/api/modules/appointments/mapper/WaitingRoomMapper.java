package com.dentalcare.api.modules.appointments.mapper;

import com.dentalcare.api.modules.appointments.dto.response.*;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.WaitingRoomEntry;
import org.springframework.stereotype.Component;

@Component
public class WaitingRoomMapper {
    public WaitingRoomEntryResponse toResponse(WaitingRoomEntry entry) {
        Appointment appointment = entry.getAppointment();
        return new WaitingRoomEntryResponse(
                entry.getId(), appointment.getId(),
                new AdministrativeAppointmentPatientResponse(
                        appointment.getPatient().getId(), appointment.getPatient().getCode(),
                        appointment.getPatient().getName(), appointment.getPatient().getPhone()),
                new AppointmentProfessionalResponse(
                        appointment.getProfessional().getId(), appointment.getProfessional().getFullName()),
                appointment.getScheduledAt(), entry.getStatus(), entry.getArrivedAt(),
                entry.getWaitingAt(), entry.getReadyAt(), entry.getClosedAt(),
                entry.getCheckedInBy().getId(), entry.getLastUpdatedBy().getId(), entry.getUpdatedAt());
    }
}
