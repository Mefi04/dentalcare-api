package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.UUID;

public interface AppointmentRequestService {
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
}
