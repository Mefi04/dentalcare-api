package com.dentalcare.api.modules.appointments.controller;

import com.dentalcare.api.modules.appointments.dto.response.AppointmentAvailabilityResponse;
import com.dentalcare.api.modules.appointments.service.AppointmentAvailabilityService;
import com.dentalcare.api.modules.users.model.ProfessionalServiceCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/appointment-availability")
@Tag(name = "Public appointment availability")
public class PublicAppointmentAvailabilityController {

    private final AppointmentAvailabilityService availabilityService;

    public PublicAppointmentAvailabilityController(AppointmentAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    @Operation(summary = "Get aggregated appointment availability for first appointments",
            description = "Returns capacity status per time slot (AVAILABLE, REQUESTED, BOOKED, UNAVAILABLE) without revealing patient or doctor identities.")
    public ResponseEntity<AppointmentAvailabilityResponse> getAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false, defaultValue = "GENERAL_DENTISTRY") ProfessionalServiceCode serviceCode) {
        return ResponseEntity.ok(availabilityService.getPublicAvailability(date, professionalId, serviceCode));
    }
}
