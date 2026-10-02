package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.appointments.dto.response.WaitingRoomEntryResponse;
import com.dentalcare.api.modules.appointments.mapper.WaitingRoomMapper;
import com.dentalcare.api.modules.appointments.model.*;
import com.dentalcare.api.modules.appointments.repository.*;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;

@Service
public class WaitingRoomServiceImpl implements WaitingRoomService {
    static final ZoneId CLINIC_ZONE = ZoneId.of("America/Guatemala");
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort ORDER = Sort.by(Sort.Order.asc("arrivedAt"), Sort.Order.asc("id"));

    private final WaitingRoomRepository waitingRoom;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final WaitingRoomMapper mapper;
    private final Clock clock;

    public WaitingRoomServiceImpl(WaitingRoomRepository waitingRoom, AppointmentRepository appointments,
                                  UserRepository users, WaitingRoomMapper mapper, Clock clock) {
        this.waitingRoom = waitingRoom;
        this.appointments = appointments;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WaitingRoomEntryResponse> findAll(LocalDate date, WaitingRoomStatus status,
                                                   UUID professionalId, int page, int size) {
        LocalDate clinicDate = date == null ? LocalDate.now(clock.withZone(CLINIC_ZONE)) : date;
        Instant from = clinicDate.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant to = clinicDate.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        Specification<WaitingRoomEntry> spec = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("appointment").get("scheduledAt"), from),
                cb.lessThan(root.get("appointment").get("scheduledAt"), to));
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (professionalId != null) spec = spec.and((root, query, cb) ->
                cb.equal(root.get("appointment").get("professional").get("id"), professionalId));
        return waitingRoom.findAll(spec, page(page, size)).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public WaitingRoomEntryResponse findByAppointmentId(UUID appointmentId) {
        return waitingRoom.findByAppointment_Id(requireAppointmentId(appointmentId))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Waiting room entry not found"));
    }

    @Override
    @Transactional
    public WaitingRoomEntryResponse checkIn(UUID actorId, UUID appointmentId) {
        User actor = actor(actorId);
        UUID validId = requireAppointmentId(appointmentId);
        Appointment appointment = appointments.findById(validId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ConflictException("Only scheduled appointments can check in");
        }
        LocalDate appointmentDate = appointment.getScheduledAt().atZone(CLINIC_ZONE).toLocalDate();
        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        if (!appointmentDate.equals(today)) {
            throw new ConflictException("Only appointments from the current clinic day can check in");
        }
        if (waitingRoom.existsByAppointment_Id(validId)) {
            throw new ConflictException("Appointment is already checked in");
        }
        WaitingRoomEntry entry = new WaitingRoomEntry(UUID.randomUUID(), appointment, actor, clock.instant());
        try {
            return mapper.toResponse(waitingRoom.saveAndFlush(entry));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Appointment is already checked in");
        }
    }

    @Override
    @Transactional
    public WaitingRoomEntryResponse advance(UUID actorId, UUID appointmentId, WaitingRoomStatus status) {
        User actor = actor(actorId);
        if (status == null) throw new BadRequestException("Waiting room status is required");
        WaitingRoomEntry entry = locked(appointmentId);
        if (entry.getAppointment().getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ConflictException("The appointment is no longer active");
        }
        WaitingRoomStatus expected = switch (entry.getStatus()) {
            case ARRIVED -> WaitingRoomStatus.WAITING;
            case WAITING -> WaitingRoomStatus.READY;
            case READY, CLOSED -> null;
        };
        if (status != expected) throw new ConflictException("Invalid waiting room status transition");
        entry.advanceTo(status, actor, clock.instant());
        return mapper.toResponse(waitingRoom.saveAndFlush(entry));
    }

    @Override
    @Transactional
    public void closeForAppointment(UUID actorId, UUID appointmentId) {
        User actor = actor(actorId);
        waitingRoom.findByAppointmentIdForUpdate(requireAppointmentId(appointmentId)).ifPresent(entry -> {
            if (entry.getStatus() != WaitingRoomStatus.CLOSED) {
                entry.close(actor, clock.instant());
                waitingRoom.saveAndFlush(entry);
            }
        });
    }

    private WaitingRoomEntry locked(UUID appointmentId) {
        return waitingRoom.findByAppointmentIdForUpdate(requireAppointmentId(appointmentId))
                .orElseThrow(() -> new ResourceNotFoundException("Waiting room entry not found"));
    }

    private User actor(UUID actorId) {
        if (actorId == null) throw new BadRequestException("Authenticated user id is required");
        return users.findById(actorId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UUID requireAppointmentId(UUID appointmentId) {
        if (appointmentId == null) throw new BadRequestException("Appointment id is required");
        return appointmentId;
    }

    private PageRequest page(int page, int size) {
        if (page < 0) throw new BadRequestException("Page must be at least 0");
        if (size < 1) throw new BadRequestException("Size must be at least 1");
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), ORDER);
    }
}
