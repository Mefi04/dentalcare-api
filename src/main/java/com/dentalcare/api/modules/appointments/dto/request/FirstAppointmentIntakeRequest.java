package com.dentalcare.api.modules.appointments.dto.request;

import com.dentalcare.api.modules.patients.model.Gender;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;

public record FirstAppointmentIntakeRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^[0-9]{13}$") String cui,
        @NotNull @Past LocalDate birthDate,
        @NotNull Gender gender,
        @Size(max = 100) String alternativeId,
        @Size(max = 150) String guardianName,
        @Size(max = 100) String guardianRelationship,
        @Pattern(regexp = "^[+0-9() .-]{7,30}$") String guardianPhone,
        @NotBlank @Pattern(regexp = "^[+0-9() .-]{7,30}$") String phone,
        @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String department,
        @NotBlank @Size(max = 100) String municipality,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 150) String emergencyName,
        @NotBlank @Pattern(regexp = "^[+0-9() .-]{7,30}$") String emergencyPhone,
        @Size(max = 30) String nit,
        @Size(max = 150) String billingName,
        @Size(max = 255) String billingAddress,
        @NotNull @Future Instant requestedAt,
        @Size(max = 300) String logisticsNote,
        @AssertTrue boolean privacyAccepted,
        @NotBlank @Size(max = 40) String privacyNoticeVersion) { }
