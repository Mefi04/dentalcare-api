package com.dentalcare.api.modules.appointments.mapper;

import com.dentalcare.api.modules.appointments.dto.response.*;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import org.springframework.stereotype.Component;

@Component
public class AppointmentRequestMapper {
    public AppointmentRequestResponse toResponse(AppointmentRequest request) {
        return new AppointmentRequestResponse(
                request.getId(),
                new AdministrativeAppointmentPatientResponse(
                        request.getPatient().getId(), request.getPatient().getCode(),
                        request.getPatient().getName(), request.getPatient().getPhone()),
                professional(request.getRequestedProfessional()),
                request.getRequestedAt(),
                professional(request.getProposedProfessional()),
                request.getProposedAt(),
                request.getStatus(),
                actionRequiredBy(request.getStatus()),
                request.getAppointment() == null ? null : request.getAppointment().getId(),
                request.getCreatedAt(), request.getUpdatedAt());
    }

    private AppointmentProfessionalResponse professional(com.dentalcare.api.modules.users.model.User user) {
        return user == null ? null : new AppointmentProfessionalResponse(user.getId(), user.getFullName());
    }

    private String actionRequiredBy(AppointmentRequestStatus status) {
        return switch (status) {
            case PENDING -> "CLINIC";
            case PROPOSED -> "PATIENT";
            case CONFIRMED, REJECTED, CANCELLED -> "NONE";
        };
    }
}
