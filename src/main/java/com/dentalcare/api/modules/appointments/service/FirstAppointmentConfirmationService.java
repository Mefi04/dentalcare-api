package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.dto.request.ConfirmFirstAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.mapper.AppointmentRequestMapper;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.modules.users.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service
public class FirstAppointmentConfirmationService {
    private final AppointmentRequestRepository requests;
    private final UserRepository users;
    private final AppointmentService appointments;
    private final GeneralDentistryAvailabilityService availability;
    private final AppointmentRequestMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public FirstAppointmentConfirmationService(AppointmentRequestRepository requests,
            UserRepository users, AppointmentService appointments,
            GeneralDentistryAvailabilityService availability, AppointmentRequestMapper mapper,
            AuditService audit, Clock clock) {
        this.requests = requests;
        this.users = users;
        this.appointments = appointments;
        this.availability = availability;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AppointmentRequestResponse confirm(UUID actorId, UUID requestId,
            ConfirmFirstAppointmentRequest input) {
        if (!input.telephoneAgreementConfirmed()) {
            throw new BadRequestException("Telephone agreement must be confirmed by reception");
        }
        var request = requests.findDetailedByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
        if (request.getRequesterFullName() == null) {
            throw new ConflictException("Only public first-appointment requests can be confirmed here");
        }
        if (request.getStatus() == AppointmentRequestStatus.CONFIRMED) {
            if (request.getAppointment() != null
                    && request.getAppointment().getProfessional().getId().equals(input.professionalId())
                    && request.getAppointment().getScheduledAt().equals(input.scheduledAt())) {
                return mapper.toAdministrativeResponse(request);
            }
            throw new ConflictException("Request has already been confirmed at another time");
        }
        if (request.getStatus() != AppointmentRequestStatus.PENDING_CLINIC) {
            throw new ConflictException("Request is not pending reception confirmation");
        }
        var actor = users.findWithRolesById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Administrative user not found"));
        if (actor.getStatus() != UserStatus.ACTIVE || actor.getRoles().stream()
                .noneMatch(role -> role.isActive() && ("SECRETARY".equals(role.getCode())
                        || "ADMINISTRATOR".equals(role.getCode())))) {
            throw new ConflictException("Actor is not authorized to confirm public appointments");
        }
        var professional = users.findWithRolesById(input.professionalId())
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found"));
        if (!availability.canBook(professional.getId(), input.scheduledAt())) {
            throw new ConflictException("Selected general-dentistry time is no longer available");
        }
        var appointment = appointments.createPublic(request.getRequesterFullName(),
                request.getRequesterPhone(), professional.getId(), input.scheduledAt());
        request.confirm(appointment, actor, clock.instant());
        audit.success(AuditActions.APPOINTMENT_REQUEST_PROCESSED, "APPOINTMENTS",
                "AppointmentRequest", requestId, actorId);
        return mapper.toAdministrativeResponse(requests.saveAndFlush(request));
    }
}
