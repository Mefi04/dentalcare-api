package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentContactAttemptRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentContactAttemptResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentContactAttempt;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentContactAttemptRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AppointmentContactService {
    private final AppointmentContactAttemptRepository attempts;
    private final AppointmentRequestRepository requests;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public AppointmentContactService(AppointmentContactAttemptRepository attempts,
            AppointmentRequestRepository requests, UserRepository users, AuditService audit, Clock clock) {
        this.attempts = attempts;
        this.requests = requests;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AppointmentContactAttemptResponse record(UUID actorId, UUID requestId,
            CreateAppointmentContactAttemptRequest input) {
        var request = requests.findDetailedByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
        if (request.getRequesterFullName() == null) {
            throw new ConflictException("Contact attempts are only available for public requests");
        }
        if (request.getStatus() != AppointmentRequestStatus.PENDING_CLINIC) {
            throw new ConflictException("Only pending public requests accept contact attempts");
        }
        var actor = users.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Administrative user not found"));
        Instant now = clock.instant();
        if (input.nextAttemptAt() != null && !input.nextAttemptAt().isAfter(now)) {
            throw new BadRequestException("Next attempt must be in the future");
        }
        String observation = input.observation() == null || input.observation().isBlank()
                ? null : input.observation().trim();
        var saved = attempts.save(new AppointmentContactAttempt(UUID.randomUUID(), request, actor, now,
                input.result(), observation, input.nextAttemptAt()));
        audit.success(AuditActions.APPOINTMENT_CONTACT_ATTEMPT_RECORDED, "APPOINTMENTS",
                "AppointmentContactAttempt", saved.getId(), actorId);
        return map(saved);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentContactAttemptResponse> history(UUID requestId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Page and size are out of range");
        }
        if (!requests.existsById(requestId)) {
            throw new ResourceNotFoundException("Appointment request not found");
        }
        return attempts.findByAppointmentRequest_Id(requestId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("attemptedAt"), Sort.Order.desc("id"))))
                .map(AppointmentContactService::map);
    }

    private static AppointmentContactAttemptResponse map(AppointmentContactAttempt attempt) {
        return new AppointmentContactAttemptResponse(attempt.getId(),
                attempt.getAppointmentRequest().getId(), attempt.getActor().getId(),
                attempt.getAttemptedAt(), attempt.getResult(), attempt.getObservation(),
                attempt.getNextAttemptAt());
    }
}
