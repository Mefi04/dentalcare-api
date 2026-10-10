package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.response.FirstAppointmentSchedulingOptionsResponse;
import com.dentalcare.api.modules.appointments.service.FirstAppointmentSchedulingOptionsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointment-requests/{requestId}/scheduling-options")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SECRETARY')")
public class FirstAppointmentSchedulingOptionsController {
    private final FirstAppointmentSchedulingOptionsService service;
    public FirstAppointmentSchedulingOptionsController(FirstAppointmentSchedulingOptionsService service) {
        this.service = service;
    }
    @GetMapping
    public FirstAppointmentSchedulingOptionsResponse get(@PathVariable UUID requestId) {
        return service.forRequest(requestId);
    }
}
