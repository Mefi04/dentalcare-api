package com.dentalcare.api.modules.appointments.dto.response;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record PublicAppointmentConversationResponse(UUID requestId, AppointmentRequestStatus status,
        AppointmentProfessionalResponse proposedProfessional, Instant proposedAt, Instant proposalExpiresAt,
        UUID appointmentId, List<AppointmentRequestMessageResponse> messages,
        Instant confirmedAt, AppointmentProfessionalResponse confirmedProfessional,
        Instant conversationExpiresAt) {
    public PublicAppointmentConversationResponse(UUID requestId, AppointmentRequestStatus status,
            AppointmentProfessionalResponse proposedProfessional, Instant proposedAt, Instant proposalExpiresAt,
            UUID appointmentId, List<AppointmentRequestMessageResponse> messages) {
        this(requestId, status, proposedProfessional, proposedAt, proposalExpiresAt,
                appointmentId, messages, null, null, null);
    }
}
