package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.dto.request.CreatePublicAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.request.ConfirmPublicAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.request.AssignAppointmentRequestProfessionalRequest;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentRequestReceipt;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.UUID;

public interface AppointmentRequestService {
    PublicAppointmentRequestReceipt createPublic(CreatePublicAppointmentRequest request, UUID idempotencyKey);
    AppointmentRequestResponse linkPublicRequestPatient(UUID actorId, UUID requestId, UUID patientId);
    AppointmentRequestResponse assignPublicRequestProfessional(UUID actorId, UUID requestId, UUID professionalId);
    AppointmentRequestResponse confirmPublicProposal(UUID actorId, UUID requestId);
    AppointmentRequestResponse confirmPublicAppointment(UUID actorId, UUID requestId, ConfirmPublicAppointmentRequest request);
    AppointmentRequestResponse createForPatient(UUID userId, UUID professionalId, Instant requestedAt);
    Page<AppointmentRequestResponse> findForPatient(UUID userId, int page, int size);
    AppointmentRequestResponse findOwned(UUID userId, UUID requestId);
    AppointmentRequestResponse acceptProposal(UUID userId, UUID requestId);
    AppointmentRequestResponse rejectProposal(UUID userId, UUID requestId);
    AppointmentRequestResponse cancelForPatient(UUID userId, UUID requestId);

    Page<AppointmentRequestResponse> findAll(Instant from, Instant to, UUID patientId,
                                             UUID professionalId, AppointmentRequestStatus status,
                                             int page, int size);

    Page<AppointmentRequestResponse> findAll(Instant from, Instant to, UUID patientId,
                                             UUID professionalId, AppointmentRequestStatus status,
                                             com.dentalcare.api.modules.appointments.model.AppointmentRequestSource source,
                                             int page, int size);

    AppointmentRequestResponse findById(UUID requestId);
    AppointmentRequestResponse acceptRequestedTime(UUID actorId, UUID requestId);
    AppointmentRequestResponse propose(UUID actorId, UUID requestId, UUID professionalId, Instant proposedAt);
    AppointmentRequestResponse reject(UUID actorId, UUID requestId);

    com.dentalcare.api.modules.appointments.dto.response.AppointmentContactAttemptResponse logContactAttempt(
            UUID actorId, UUID requestId, com.dentalcare.api.modules.appointments.dto.request.CreateContactAttemptRequest request);

    Page<com.dentalcare.api.modules.appointments.dto.response.AppointmentContactAttemptResponse> findContactAttempts(
            UUID requestId, int page, int size);
}
