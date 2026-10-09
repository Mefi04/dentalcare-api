package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.dto.request.CreatePublicAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.request.AssignAppointmentRequestProfessionalRequest;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentRequestReceipt;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.UUID;
import com.dentalcare.api.modules.appointments.dto.request.PublicAppointmentDecisionRequest;
import com.dentalcare.api.modules.appointments.dto.request.PublicVerificationChannelRequest;
import com.dentalcare.api.modules.appointments.dto.request.VerifyPublicAppointmentCodeRequest;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentConversationResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicConversationTokenResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicVerificationAcknowledgement;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.dto.request.ClinicSchedulingMessageRequest;
import java.time.LocalDate;

public interface AppointmentRequestService {
    PublicAppointmentRequestReceipt createPublic(CreatePublicAppointmentRequest request, UUID idempotencyKey);
    AppointmentRequestResponse linkPublicRequestPatient(UUID actorId, UUID requestId, UUID patientId);
    AppointmentRequestResponse assignPublicRequestProfessional(UUID actorId, UUID requestId, UUID professionalId);
    AppointmentRequestResponse confirmPublicProposal(UUID actorId, UUID requestId);
    AppointmentRequestResponse createForPatient(UUID userId, UUID professionalId, Instant requestedAt);
    Page<AppointmentRequestResponse> findForPatient(UUID userId, int page, int size);
    AppointmentRequestResponse findOwned(UUID userId, UUID requestId);
    AppointmentRequestResponse acceptProposal(UUID userId, UUID requestId);
    AppointmentRequestResponse rejectProposal(UUID userId, UUID requestId);
    AppointmentRequestResponse cancelForPatient(UUID userId, UUID requestId);

    Page<AppointmentRequestResponse> findAll(Instant from, Instant to, UUID patientId,
                                             UUID professionalId, AppointmentRequestStatus status,
                                             int page, int size);
    AppointmentRequestResponse findById(UUID requestId);
    AppointmentRequestResponse acceptRequestedTime(UUID actorId, UUID requestId);
    AppointmentRequestResponse propose(UUID actorId, UUID requestId, UUID professionalId, Instant proposedAt);
    AppointmentRequestResponse reject(UUID actorId, UUID requestId);
    PublicVerificationAcknowledgement requestConversationCode(UUID requestId, PublicVerificationChannelRequest request);
    PublicConversationTokenResponse verifyConversationCode(UUID requestId, VerifyPublicAppointmentCodeRequest request);
    PublicAppointmentConversationResponse getPublicConversation(UUID requestId, String token);
    PublicAppointmentConversationResponse decidePublicProposal(UUID requestId, String token,
                                                               PublicAppointmentDecisionRequest request, UUID idempotencyKey);
    AppointmentAvailabilityResponse getAvailability(UUID professionalId, LocalDate date);
    AppointmentRequestResponse addSchedulingMessage(UUID actorId, UUID requestId, ClinicSchedulingMessageRequest request);
}
