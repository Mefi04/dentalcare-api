package com.dentalcare.api.modules.appointments.mapper;

import com.dentalcare.api.modules.appointments.dto.response.*;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import org.springframework.stereotype.Component;

@Component
public class AppointmentRequestMapper {
    public AppointmentRequestResponse toResponse(AppointmentRequest request) {
        return toResponse(request, false);
    }

    public AppointmentRequestResponse toAdministrativeResponse(AppointmentRequest request) {
        return toResponse(request, true);
    }

    private AppointmentRequestResponse toResponse(AppointmentRequest request, boolean administrative) {
        boolean publicRequest = request.getRequesterFullName() != null;
        PublicAppointmentRequesterResponse contact = publicRequest ? new PublicAppointmentRequesterResponse(
                request.getRequesterFullName(),
                request.getRequesterCui(),
                request.getRequesterPhone(),
                request.getRequesterEmail(),
                request.getRequestReason(),
                request.getBirthDate(),
                request.getGender(),
                request.getAlternativeId(),
                request.getGuardianName(),
                request.getGuardianRelationship(),
                request.getGuardianPhone(),
                request.getDepartment(),
                request.getMunicipality(),
                request.getAddress(),
                request.getEmergencyName(),
                request.getEmergencyPhone(),
                request.getNit(),
                request.getBillingName(),
                request.getBillingAddress(),
                request.getPrivacyAccepted()) : null;
        return new AppointmentRequestResponse(
                request.getId(),
                request.getPatient() == null ? null : new AdministrativeAppointmentPatientResponse(
                        request.getPatient().getId(), request.getPatient().getCode(),
                        request.getPatient().getName(), request.getPatient().getPhone()),
                professional(request.getRequestedProfessional()),
                request.getRequestedAt(),
                professional(request.getProposedProfessional()),
                request.getProposedAt(),
                request.getStatus(),
                actionRequiredBy(request.getStatus()),
                request.getAppointment() == null ? null : request.getAppointment().getId(),
                request.getCreatedAt(), request.getUpdatedAt(),
                contact,
                administrative ? (request.getSource() != null ? request.getSource().name() : (publicRequest ? "PUBLIC" : "PATIENT_PORTAL")) : null,
                administrative ? contact : null,
                administrative && publicRequest ? professional(request.getAssignedProfessional()) : null);
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
