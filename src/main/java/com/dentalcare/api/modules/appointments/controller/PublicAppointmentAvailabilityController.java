package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.response.PublicAppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.service.GeneralDentistryAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/public/appointment-availability")
@Tag(name = "Public first appointment availability")
public class PublicAppointmentAvailabilityController {
    private final GeneralDentistryAvailabilityService service;

    public PublicAppointmentAvailabilityController(GeneralDentistryAvailabilityService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Read aggregate general dentistry availability without patient or dentist identities")
    public PublicAppointmentAvailabilityResponse forDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.forDate(date);
    }
}
