package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentSlotResponse;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.SlotStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.users.model.ProfessionalPublicProfile;
import com.dentalcare.api.modules.users.model.ProfessionalServiceCode;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.ProfessionalPublicProfileRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AppointmentAvailabilityServiceImpl implements AppointmentAvailabilityService {

    public static final ZoneId CLINIC_ZONE = ZoneId.of("America/Guatemala");
    public static final int SLOT_DURATION_MINUTES = 45;

    private static final List<LocalTime> WEEKDAY_SLOTS = List.of(
            LocalTime.of(8, 0),
            LocalTime.of(8, 45),
            LocalTime.of(9, 30),
            LocalTime.of(10, 15),
            LocalTime.of(11, 0),
            LocalTime.of(11, 45),
            LocalTime.of(13, 0),
            LocalTime.of(13, 45),
            LocalTime.of(14, 30),
            LocalTime.of(15, 15),
            LocalTime.of(16, 0)
    );

    private static final List<LocalTime> SATURDAY_SLOTS = List.of(
            LocalTime.of(8, 0),
            LocalTime.of(8, 45),
            LocalTime.of(9, 30),
            LocalTime.of(10, 15),
            LocalTime.of(11, 0),
            LocalTime.of(11, 45)
    );

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointments;
    private final AppointmentRequestRepository requests;
    private final ProfessionalPublicProfileRepository profiles;
    private final UserRepository users;
    private final Clock clock;

    public AppointmentAvailabilityServiceImpl(AppointmentRepository appointments,
                                              AppointmentRequestRepository requests,
                                              ProfessionalPublicProfileRepository profiles,
                                              UserRepository users,
                                              Clock clock) {
        this.appointments = appointments;
        this.requests = requests;
        this.profiles = profiles;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentAvailabilityResponse getPublicAvailability(LocalDate date, UUID professionalId,
                                                                 ProfessionalServiceCode serviceCode) {
        if (date == null) throw new BadRequestException("Date is required");
        ProfessionalServiceCode resolvedCode = serviceCode != null ? serviceCode : ProfessionalServiceCode.GENERAL_DENTISTRY;

        List<User> eligibleDentists = resolvePublicDentists(professionalId, resolvedCode);
        List<AppointmentSlotResponse> slots = calculateSlots(date, eligibleDentists, professionalId);

        return new AppointmentAvailabilityResponse(date, resolvedCode.name(), professionalId, slots);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentAvailabilityResponse getPatientAvailability(LocalDate date, UUID professionalId,
                                                                  ProfessionalServiceCode serviceCode) {
        if (date == null) throw new BadRequestException("Date is required");
        List<User> eligibleDentists = resolvePatientDentists(professionalId, serviceCode);
        List<AppointmentSlotResponse> slots = calculateSlots(date, eligibleDentists, professionalId);

        return new AppointmentAvailabilityResponse(
                date, serviceCode != null ? serviceCode.name() : null, professionalId, slots);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentAvailabilityResponse getAdministrativeAvailability(LocalDate date, UUID professionalId,
                                                                          ProfessionalServiceCode serviceCode) {
        if (date == null) throw new BadRequestException("Date is required");
        if (professionalId == null) throw new BadRequestException("Professional is required");
        User professional = users.findWithRolesById(professionalId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getRoles().stream()
                        .anyMatch(role -> role.isActive() && "DENTIST".equals(role.getCode())))
                .orElseThrow(() -> new BadRequestException("Selected professional must be an active dentist"));
        ProfessionalServiceCode resolvedCode = serviceCode != null ? serviceCode : ProfessionalServiceCode.GENERAL_DENTISTRY;
        List<AppointmentSlotResponse> slots = calculateSlots(date, List.of(professional), professionalId);
        return new AppointmentAvailabilityResponse(date, resolvedCode.name(), professionalId, slots);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentSlotResponse> getAvailableSlotsForGemini(LocalDate date) {
        AppointmentAvailabilityResponse response = getPublicAvailability(date, null, ProfessionalServiceCode.GENERAL_DENTISTRY);
        return response.slots().stream()
                .filter(slot -> slot.status() == SlotStatus.AVAILABLE || slot.status() == SlotStatus.REQUESTED)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSlotAvailableForBooking(Instant scheduledAt, UUID professionalId) {
        if (scheduledAt == null || !scheduledAt.isAfter(clock.instant())) {
            return false;
        }

        ZonedDateTime localDateTime = scheduledAt.atZone(CLINIC_ZONE);
        LocalDate date = localDateTime.toLocalDate();
        LocalTime time = localDateTime.toLocalTime();

        DayOfWeek dow = date.getDayOfWeek();
        List<LocalTime> validTimes = dow == DayOfWeek.SUNDAY ? List.of() :
                (dow == DayOfWeek.SATURDAY ? SATURDAY_SLOTS : WEEKDAY_SLOTS);

        if (!validTimes.contains(time)) {
            return false;
        }

        if (professionalId != null) {
            return !appointments.existsByProfessional_IdAndScheduledAtAndStatus(
                    professionalId, scheduledAt, com.dentalcare.api.modules.appointments.model.AppointmentStatus.SCHEDULED);
        }

        List<User> publicDentists = resolvePublicDentists(null, ProfessionalServiceCode.GENERAL_DENTISTRY);
        if (publicDentists.isEmpty()) return false;

        Instant startOfDay = date.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        List<Appointment> dayAppointments = appointments.findScheduledBetween(startOfDay, endOfDay);

        long busyDentists = dayAppointments.stream()
                .filter(a -> a.getScheduledAt().equals(scheduledAt))
                .map(a -> a.getProfessional().getId())
                .distinct()
                .count();

        return busyDentists < publicDentists.size();
    }

    private List<AppointmentSlotResponse> calculateSlots(LocalDate date, List<User> eligibleDentists,
                                                         UUID selectedProfessionalId) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SUNDAY || eligibleDentists.isEmpty()) {
            return List.of();
        }

        List<LocalTime> slotTimes = dow == DayOfWeek.SATURDAY ? SATURDAY_SLOTS : WEEKDAY_SLOTS;
        Instant startOfDay = date.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();

        List<Appointment> scheduledAppointments = appointments.findScheduledBetween(startOfDay, endOfDay);
        List<AppointmentRequest> activeRequests = requests.findActiveRequestsBetween(startOfDay, endOfDay);
        Instant now = clock.instant();

        int totalCapacity = eligibleDentists.size();
        Set<UUID> eligibleDentistIds = new HashSet<>(eligibleDentists.stream().map(User::getId).toList());

        List<AppointmentSlotResponse> result = new ArrayList<>();
        for (LocalTime slotTime : slotTimes) {
            Instant slotInstant = date.atTime(slotTime).atZone(CLINIC_ZONE).toInstant();
            String localTimeStr = slotTime.format(TIME_FORMATTER);

            if (!slotInstant.isAfter(now)) {
                result.add(new AppointmentSlotResponse(
                        slotInstant, localTimeStr, SlotStatus.UNAVAILABLE, 0, totalCapacity));
                continue;
            }

            Set<UUID> busyDentistIds = new HashSet<>();
            for (Appointment a : scheduledAppointments) {
                if (a.getScheduledAt().equals(slotInstant) && eligibleDentistIds.contains(a.getProfessional().getId())) {
                    busyDentistIds.add(a.getProfessional().getId());
                }
            }

            int availableCapacity = Math.max(0, totalCapacity - busyDentistIds.size());

            long pendingDemands = activeRequests.stream()
                    .filter(r -> (slotInstant.equals(r.getRequestedAt()) || slotInstant.equals(r.getProposedAt()))
                            && (selectedProfessionalId == null || requestTargetsProfessional(r, selectedProfessionalId)))
                    .count();

            SlotStatus status;
            if (availableCapacity == 0) {
                status = SlotStatus.BOOKED;
            } else if (pendingDemands > 0) {
                status = SlotStatus.REQUESTED;
            } else {
                status = SlotStatus.AVAILABLE;
            }

            result.add(new AppointmentSlotResponse(
                    slotInstant, localTimeStr, status, availableCapacity, totalCapacity));
        }

        return result;
    }

    private boolean requestTargetsProfessional(AppointmentRequest request, UUID professionalId) {
        return (request.getRequestedProfessional() != null && professionalId.equals(request.getRequestedProfessional().getId()))
                || (request.getAssignedProfessional() != null && professionalId.equals(request.getAssignedProfessional().getId()))
                || (request.getProposedProfessional() != null && professionalId.equals(request.getProposedProfessional().getId()));
    }

    private List<User> resolvePublicDentists(UUID professionalId, ProfessionalServiceCode serviceCode) {
        if (professionalId != null) {
            return profiles.findPubliclyVisibleByUserId(professionalId)
                    .filter(p -> p.getServiceCode() == serviceCode)
                    .map(ProfessionalPublicProfile::getUser)
                    .map(List::of)
                    .orElse(List.of());
        }

        return profiles.findPubliclyVisibleByServiceCode(serviceCode).stream()
                .map(ProfessionalPublicProfile::getUser)
                .toList();
    }

    private List<User> resolvePatientDentists(UUID professionalId, ProfessionalServiceCode serviceCode) {
        if (professionalId != null) {
            return users.findWithRolesById(professionalId)
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getRoles().stream()
                            .anyMatch(r -> r.isActive() && "DENTIST".equals(r.getCode())))
                    .map(List::of)
                    .orElse(List.of());
        }

        if (serviceCode != null) {
            return profiles.findPubliclyVisibleByServiceCode(serviceCode).stream()
                    .map(ProfessionalPublicProfile::getUser)
                    .toList();
        }

        return profiles.findAllPubliclyVisible().stream()
                .map(ProfessionalPublicProfile::getUser)
                .toList();
    }
}
