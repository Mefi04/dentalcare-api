package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.dto.request.CreateProfessionalScheduleBlockRequest;
import com.dentalcare.api.modules.appointments.dto.request.CreateProfessionalWorkIntervalRequest;
import com.dentalcare.api.modules.appointments.dto.response.ProfessionalScheduleBlockResponse;
import com.dentalcare.api.modules.appointments.dto.response.ProfessionalWorkIntervalResponse;
import com.dentalcare.api.modules.appointments.model.ProfessionalScheduleBlock;
import com.dentalcare.api.modules.appointments.model.ProfessionalWorkInterval;
import com.dentalcare.api.modules.appointments.repository.ProfessionalScheduleBlockRepository;
import com.dentalcare.api.modules.appointments.repository.ProfessionalWorkIntervalRepository;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ProfessionalScheduleService {
    private final ProfessionalWorkIntervalRepository work;
    private final ProfessionalScheduleBlockRepository blocks;
    private final GeneralDentistryAvailabilityService availability;
    private final AuditService audit;
    private final Clock clock;

    public ProfessionalScheduleService(ProfessionalWorkIntervalRepository work,
            ProfessionalScheduleBlockRepository blocks,
            GeneralDentistryAvailabilityService availability, AuditService audit, Clock clock) {
        this.work = work; this.blocks = blocks; this.availability = availability;
        this.audit = audit; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProfessionalWorkIntervalResponse> workIntervals(UUID professionalId) {
        return work.findByProfessionalIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(professionalId)
                .stream().map(value -> new ProfessionalWorkIntervalResponse(value.getId(), professionalId,
                        value.getDayOfWeek(), value.getStartTime(), value.getEndTime())).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfessionalScheduleBlockResponse> futureBlocks(UUID professionalId) {
        return blocks.findByProfessionalIdAndEndsAtGreaterThanOrderByStartsAtAsc(
                professionalId, clock.instant()).stream()
                .map(value -> new ProfessionalScheduleBlockResponse(value.getId(), professionalId,
                        value.getStartsAt(), value.getEndsAt(), value.getReason())).toList();
    }

    @Transactional
    public ProfessionalWorkIntervalResponse addWork(UUID actorId, UUID professionalId,
            CreateProfessionalWorkIntervalRequest input) {
        requireGeneralDentist(professionalId);
        if (!input.startTime().isBefore(input.endTime())) {
            throw new BadRequestException("Work interval start must precede end");
        }
        boolean overlaps = work.findByProfessionalIdInAndDayOfWeekAndActiveTrue(
                List.of(professionalId), input.dayOfWeek()).stream()
                .anyMatch(existing -> input.startTime().isBefore(existing.getEndTime())
                        && existing.getStartTime().isBefore(input.endTime()));
        if (overlaps) throw new ConflictException("Work interval overlaps another interval");
        var saved = work.save(new ProfessionalWorkInterval(UUID.randomUUID(), professionalId,
                input.dayOfWeek(), input.startTime(), input.endTime()));
        audit.success(AuditActions.PROFESSIONAL_SCHEDULE_CHANGED, "APPOINTMENTS",
                "ProfessionalWorkInterval", saved.getId(), actorId);
        return new ProfessionalWorkIntervalResponse(saved.getId(), professionalId,
                saved.getDayOfWeek(), saved.getStartTime(), saved.getEndTime());
    }

    @Transactional
    public ProfessionalScheduleBlockResponse addBlock(UUID actorId, UUID professionalId,
            CreateProfessionalScheduleBlockRequest input) {
        requireGeneralDentist(professionalId);
        if (!input.startsAt().isBefore(input.endsAt()) || !input.endsAt().isAfter(clock.instant())) {
            throw new BadRequestException("Schedule block must have a future end after its start");
        }
        var saved = blocks.save(new ProfessionalScheduleBlock(UUID.randomUUID(), professionalId,
                input.startsAt(), input.endsAt(), input.reason()));
        audit.success(AuditActions.PROFESSIONAL_SCHEDULE_CHANGED, "APPOINTMENTS",
                "ProfessionalScheduleBlock", saved.getId(), actorId);
        return new ProfessionalScheduleBlockResponse(saved.getId(), professionalId,
                saved.getStartsAt(), saved.getEndsAt(), saved.getReason());
    }

    @Transactional
    public void removeWork(UUID actorId, UUID professionalId, UUID intervalId) {
        var interval = work.findById(intervalId)
                .filter(value -> value.getProfessionalId().equals(professionalId))
                .orElseThrow(() -> new ResourceNotFoundException("Work interval not found"));
        work.delete(interval);
        audit.success(AuditActions.PROFESSIONAL_SCHEDULE_CHANGED, "APPOINTMENTS",
                "ProfessionalWorkInterval", intervalId, actorId);
    }

    @Transactional
    public void removeBlock(UUID actorId, UUID professionalId, UUID blockId) {
        var block = blocks.findById(blockId)
                .filter(value -> value.getProfessionalId().equals(professionalId))
                .orElseThrow(() -> new ResourceNotFoundException("Schedule block not found"));
        blocks.delete(block);
        audit.success(AuditActions.PROFESSIONAL_SCHEDULE_CHANGED, "APPOINTMENTS",
                "ProfessionalScheduleBlock", blockId, actorId);
    }

    private void requireGeneralDentist(UUID professionalId) {
        if (!availability.isGeneralDentist(professionalId)) {
            throw new ConflictException("Professional is not an active general dentist");
        }
    }
}
