package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.response.ReceptionFirstAppointmentItem;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.service.ReceptionFirstAppointmentInboxService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/appointment-requests/public-inbox")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY')")
public class ReceptionFirstAppointmentInboxController {
    private final ReceptionFirstAppointmentInboxService service;
    public ReceptionFirstAppointmentInboxController(ReceptionFirstAppointmentInboxService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ReceptionFirstAppointmentItem> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) AppointmentRequestStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(from, to, status, search, page, size);
    }
}
