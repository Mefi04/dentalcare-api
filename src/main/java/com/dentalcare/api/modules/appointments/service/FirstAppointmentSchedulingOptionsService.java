package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.dto.response.FirstAppointmentSchedulingOptionsResponse;
import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentSlotResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Clock;
import java.util.ArrayList;

@Service
public class FirstAppointmentSchedulingOptionsService {
    private final AppointmentRequestRepository requests;
    private final GeneralDentistryAvailabilityService availability;
    private final Clock clock;

    public FirstAppointmentSchedulingOptionsService(AppointmentRequestRepository requests,
            GeneralDentistryAvailabilityService availability, Clock clock) {
        this.requests = requests;
        this.availability = availability;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FirstAppointmentSchedulingOptionsResponse forRequest(java.util.UUID requestId) {
        var request = requests.findDetailedById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
        if (request.getRequesterFullName() == null || request.getStatus() != AppointmentRequestStatus.PENDING_CLINIC) {
            throw new ConflictException("Scheduling options require a pending public request");
        }
        LocalDate date = request.getRequestedAt().atZone(GeneralDentistryAvailabilityService.CLINIC_ZONE)
                .toLocalDate();
        LocalDate today = LocalDate.now(clock.withZone(GeneralDentistryAvailabilityService.CLINIC_ZONE));
        var preferred = date.isBefore(today) || date.isAfter(today.plusMonths(3)) ? null
                : availability.forDate(date).slots().stream()
                        .filter(slot -> slot.startsAt().equals(request.getRequestedAt()))
                        .findFirst().orElse(null);
        String status = preferred == null ? "UNAVAILABLE" : preferred.status();
        boolean conflict = status.equals("BOOKED") || status.equals("UNAVAILABLE");
        var alternatives = new ArrayList<PublicAppointmentSlotResponse>();
        LocalDate searchStart = date.isBefore(today) ? today : date;
        for (int day = 0; day < 14 && alternatives.size() < 10; day++) {
            var candidateDate = searchStart.plusDays(day);
            if (candidateDate.isAfter(today.plusMonths(3))) break;
            for (var slot : availability.forDate(candidateDate).slots()) {
                if (alternatives.size() == 10) break;
                if (!slot.startsAt().equals(request.getRequestedAt())
                        && (slot.status().equals("AVAILABLE") || slot.status().equals("REQUESTED"))) {
                    alternatives.add(slot);
                }
            }
        }
        return new FirstAppointmentSchedulingOptionsResponse(request.getRequestedAt(),
                status, conflict, alternatives);
    }
}
