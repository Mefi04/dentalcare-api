package com.dentalcare.api.modules.appointments.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record VerifyPublicAppointmentCodeRequest(@NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {}
