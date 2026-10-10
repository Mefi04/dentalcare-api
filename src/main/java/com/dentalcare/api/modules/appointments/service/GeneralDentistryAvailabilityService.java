package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentSlotResponse;
import com.dentalcare.api.modules.appointments.model.*;
import com.dentalcare.api.modules.appointments.repository.*;
import com.dentalcare.api.modules.users.repository.ProfessionalPublicProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GeneralDentistryAvailabilityService {
    public static final ZoneId CLINIC_ZONE = ZoneId.of("America/Guatemala");
    public static final int DURATION_MINUTES = 30;

    private final ProfessionalPublicProfileRepository profiles;
    private final ProfessionalWorkIntervalRepository hours;
    private final ProfessionalScheduleBlockRepository blocks;
    private final AppointmentRepository appointments;
    private final AppointmentRequestRepository requests;
    private final Clock clock;

    public GeneralDentistryAvailabilityService(ProfessionalPublicProfileRepository profiles,
            ProfessionalWorkIntervalRepository hours, ProfessionalScheduleBlockRepository blocks,
            AppointmentRepository appointments, AppointmentRequestRepository requests, Clock clock) {
        this.profiles = profiles;
        this.hours = hours;
        this.blocks = blocks;
        this.appointments = appointments;
        this.requests = requests;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicAppointmentAvailabilityResponse forDate(LocalDate date) {
        if (date == null || date.isBefore(LocalDate.now(clock.withZone(CLINIC_ZONE)))
                || date.isAfter(LocalDate.now(clock.withZone(CLINIC_ZONE)).plusMonths(3))) {
            throw new BadRequestException("Availability date must be within the next three months");
        }
        Instant from = date.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        var ids = profiles.findActiveGeneralDentists().stream()
                .map(profile -> profile.getUser().getId()).distinct().toList();
        if (ids.isEmpty()) return empty(date);
        var work = hours.findByProfessionalIdInAndDayOfWeekAndActiveTrue(ids, date.getDayOfWeek().getValue())
                .stream().collect(Collectors.groupingBy(ProfessionalWorkInterval::getProfessionalId));
        var blocked = blocks.findByProfessionalIdInAndStartsAtLessThanAndEndsAtGreaterThan(ids, to, from)
                .stream().collect(Collectors.groupingBy(ProfessionalScheduleBlock::getProfessionalId));
        var booked = appointments.findByProfessional_IdInAndStatusAndScheduledAtLessThanAndEndsAtGreaterThan(
                ids, AppointmentStatus.SCHEDULED, to, from.minusSeconds(4 * 3600))
                .stream().collect(Collectors.groupingBy(a -> a.getProfessional().getId()));
        var pending = requests.findPendingPublicInWindow(from, to).stream()
                .collect(Collectors.groupingBy(AppointmentRequest::getRequestedAt, Collectors.counting()));
        List<PublicAppointmentSlotResponse> slots = new ArrayList<>();
        for (int index = 0; index < 24 * 60 / DURATION_MINUTES; index++) {
            LocalTime time = LocalTime.MIDNIGHT.plusMinutes((long) index * DURATION_MINUTES);
            final LocalTime slotTime = time;
            Instant start = date.atTime(time).atZone(CLINIC_ZONE).toInstant();
            Instant end = start.plusSeconds(DURATION_MINUTES * 60L);
            int free = 0;
            int eligible = 0;
            for (UUID id : ids) {
                boolean working = work.getOrDefault(id, List.of()).stream()
                        .anyMatch(w -> !slotTime.isBefore(w.getStartTime())
                                && !slotTime.plusMinutes(DURATION_MINUTES).isAfter(w.getEndTime()));
                if (!working) continue;
                boolean isBlocked = blocked.getOrDefault(id, List.of()).stream()
                        .anyMatch(b -> overlaps(start, end, b.getStartsAt(), b.getEndsAt()));
                if (isBlocked) continue;
                eligible++;
                boolean isBooked = booked.getOrDefault(id, List.of()).stream()
                        .anyMatch(a -> overlaps(start, end, a.getScheduledAt(), a.getEndsAt()));
                if (!isBooked) free++;
            }
            int count = pending.getOrDefault(start, 0L).intValue();
            String status = !start.isAfter(clock.instant()) || eligible == 0 ? "UNAVAILABLE"
                    : free == 0 ? "BOOKED" : count > 0 ? "REQUESTED" : "AVAILABLE";
            slots.add(new PublicAppointmentSlotResponse(start, status, free, count));
        }
        return new PublicAppointmentAvailabilityResponse(date, CLINIC_ZONE.getId(), DURATION_MINUTES, slots);
    }

    public boolean isGeneralDentist(UUID professionalId) {
        return profiles.findActiveGeneralDentists().stream()
                .anyMatch(profile -> profile.getUser().getId().equals(professionalId));
    }

    @Transactional(readOnly = true)
    public boolean canBook(UUID professionalId, Instant start) {
        if (professionalId == null || start == null || !start.isAfter(clock.instant())
                || !isGeneralDentist(professionalId)) return false;
        var local = start.atZone(CLINIC_ZONE);
        if (local.getMinute() % DURATION_MINUTES != 0 || local.getSecond() != 0) return false;
        Instant end = start.plusSeconds(DURATION_MINUTES * 60L);
        var ids = List.of(professionalId);
        boolean working = hours.findByProfessionalIdInAndDayOfWeekAndActiveTrue(ids,
                local.getDayOfWeek().getValue()).stream().anyMatch(w ->
                !local.toLocalTime().isBefore(w.getStartTime())
                        && !local.toLocalTime().plusMinutes(DURATION_MINUTES).isAfter(w.getEndTime()));
        if (!working) return false;
        if (!blocks.findByProfessionalIdInAndStartsAtLessThanAndEndsAtGreaterThan(ids, end, start)
                .isEmpty()) return false;
        return !appointments.existsByProfessional_IdAndStatusAndScheduledAtLessThanAndEndsAtGreaterThan(
                professionalId, AppointmentStatus.SCHEDULED, end, start);
    }

    static boolean overlaps(Instant start, Instant end, Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    private PublicAppointmentAvailabilityResponse empty(LocalDate date) {
        return new PublicAppointmentAvailabilityResponse(date, CLINIC_ZONE.getId(),
                DURATION_MINUTES, List.of());
    }
}
