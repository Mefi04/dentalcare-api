package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.appointments.dto.response.ReceptionFirstAppointmentItem;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReceptionFirstAppointmentInboxService {
    private final AppointmentRequestRepository requests;
    private final GeneralDentistryAvailabilityService availability;

    public ReceptionFirstAppointmentInboxService(AppointmentRequestRepository requests,
            GeneralDentistryAvailabilityService availability) {
        this.requests = requests; this.availability = availability;
    }

    @Transactional(readOnly = true)
    public Page<ReceptionFirstAppointmentItem> search(Instant from, Instant to,
            AppointmentRequestStatus status, String text, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("Invalid pagination");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("From date must not follow to date");
        }
        if (text != null && text.length() > 100) throw new BadRequestException("Search is too long");
        Specification<AppointmentRequest> filter = (root, query, cb) ->
                cb.isNotNull(root.get("requesterFullName"));
        if (from != null) filter = filter.and((root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("requestedAt"), from));
        if (to != null) filter = filter.and((root, query, cb) ->
                cb.lessThan(root.get("requestedAt"), to));
        if (status != null) filter = filter.and((root, query, cb) ->
                cb.equal(root.get("status"), status));
        if (text != null && !text.isBlank()) {
            String pattern = "%" + text.trim().toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            filter = filter.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("requesterFullName")), pattern, '\\'),
                    cb.like(cb.lower(root.get("requesterPhone")), pattern, '\\'),
                    cb.like(cb.lower(root.get("requesterEmail")), pattern, '\\')));
        }
        var rows = requests.findAll(filter, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<LocalDate, Map<Instant, String>> byDay = new HashMap<>();
        return rows.map(row -> {
            boolean conflict = false;
            if (row.getStatus() == AppointmentRequestStatus.PENDING_CLINIC) {
                LocalDate day = row.getRequestedAt().atZone(
                        GeneralDentistryAvailabilityService.CLINIC_ZONE).toLocalDate();
                Map<Instant, String> slots = byDay.computeIfAbsent(day, this::statuses);
                String slotStatus = slots.getOrDefault(row.getRequestedAt(), "UNAVAILABLE");
                conflict = slotStatus.equals("BOOKED") || slotStatus.equals("UNAVAILABLE");
            }
            return new ReceptionFirstAppointmentItem(row.getId(), row.getRequesterFullName(),
                    row.getRequesterPhone(), row.getRequestedAt(), row.getStatus(), conflict,
                    row.getCreatedAt());
        });
    }

    private Map<Instant, String> statuses(LocalDate day) {
        try {
            Map<Instant, String> result = new HashMap<>();
            availability.forDate(day).slots().forEach(slot ->
                    result.put(slot.startsAt(), slot.status()));
            return result;
        } catch (BadRequestException exception) {
            return Map.of();
        }
    }
}
