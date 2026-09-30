package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.dto.request.CreateAdministrativeAppointmentRequest;
import com.dentalcare.api.modules.appointments.dto.response.AdministrativeAppointmentResponse;
import com.dentalcare.api.modules.appointments.mapper.AdministrativeAppointmentMapper;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.repository.AdministrativeAppointmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdministrativeAppointmentServiceImpl implements AdministrativeAppointmentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort APPOINTMENT_ORDER = Sort.by(
            Sort.Order.asc("scheduledAt"), Sort.Order.asc("id"));

    private final AdministrativeAppointmentRepository administrativeAppointmentRepository;
    private final AppointmentService appointmentService;
    private final AdministrativeAppointmentMapper administrativeAppointmentMapper;
    private final Clock clock;

    public AdministrativeAppointmentServiceImpl(
            AdministrativeAppointmentRepository administrativeAppointmentRepository,
            AppointmentService appointmentService,
            AdministrativeAppointmentMapper administrativeAppointmentMapper,
            Clock clock) {
        this.administrativeAppointmentRepository = administrativeAppointmentRepository;
        this.appointmentService = appointmentService;
        this.administrativeAppointmentMapper = administrativeAppointmentMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdministrativeAppointmentResponse> findAll(
            Instant from, Instant to, UUID patientId, UUID professionalId,
            AppointmentStatus status, int page, int size) {
        validatePage(page, size);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("From date must not be after to date");
        }
        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), APPOINTMENT_ORDER);
        Specification<Appointment> filters = Specification.unrestricted();
        if (from != null) {
            filters = filters.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("scheduledAt"), from));
        }
        if (to != null) {
            filters = filters.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("scheduledAt"), to));
        }
        if (patientId != null) {
            filters = filters.and((root, query, builder) ->
                    builder.equal(root.get("patient").get("id"), patientId));
        }
        if (professionalId != null) {
            filters = filters.and((root, query, builder) ->
                    builder.equal(root.get("professional").get("id"), professionalId));
        }
        if (status != null) {
            filters = filters.and((root, query, builder) ->
                    builder.equal(root.get("status"), status));
        }
        return administrativeAppointmentRepository.findAll(filters, pageable)
                .map(administrativeAppointmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdministrativeAppointmentResponse findById(UUID appointmentId) {
        requireAppointmentId(appointmentId);
        return administrativeAppointmentRepository.findDetailedById(appointmentId)
                .map(administrativeAppointmentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    @Override
    @Transactional
    public AdministrativeAppointmentResponse create(CreateAdministrativeAppointmentRequest request) {
        if (request == null) {
            throw new BadRequestException("Appointment is required");
        }
        Appointment appointment = appointmentService.create(
                request.patientId(), request.professionalId(), request.scheduledAt());
        return administrativeAppointmentMapper.toResponse(appointment);
    }

    @Override
    @Transactional
    public AdministrativeAppointmentResponse reschedule(UUID appointmentId, Instant scheduledAt) {
        requireAppointmentId(appointmentId);
        Appointment appointment = findForUpdate(appointmentId);
        return administrativeAppointmentMapper.toResponse(
                appointmentService.reschedule(appointment, scheduledAt));
    }

    @Override
    @Transactional
    public AdministrativeAppointmentResponse updateStatus(UUID appointmentId, AppointmentStatus status) {
        requireAppointmentId(appointmentId);
        if (status == null) {
            throw new BadRequestException("Appointment status is required");
        }

        Appointment appointment = findForUpdate(appointmentId);
        if (status == AppointmentStatus.CANCELLED) {
            return administrativeAppointmentMapper.toResponse(appointmentService.cancel(appointment));
        }
        if (appointment.getStatus() == status) {
            throw new ConflictException("Appointment already has the requested status");
        }
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED
                || status != AppointmentStatus.COMPLETED) {
            throw new ConflictException("Invalid appointment status transition");
        }

        appointment.setStatus(status);
        appointment.setUpdatedAt(clock.instant());
        return save(appointment);
    }

    private Appointment findForUpdate(UUID appointmentId) {
        return administrativeAppointmentRepository.findDetailedByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private AdministrativeAppointmentResponse save(Appointment appointment) {
        try {
            Appointment saved = administrativeAppointmentRepository.saveAndFlush(appointment);
            return administrativeAppointmentMapper.toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Appointment time is not available");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page must be at least 0");
        }
        if (size < 1) {
            throw new BadRequestException("Size must be at least 1");
        }
    }

    private void requireAppointmentId(UUID appointmentId) {
        if (appointmentId == null) {
            throw new BadRequestException("Appointment id is required");
        }
    }
}
