package com.dentalcare.api.modules.clinicalrecords.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateClinicalPreparationRequest(
        UUID attentionId,

        @Size(max = 20, message = "Blood pressure cannot exceed 20 characters")
        @Pattern(regexp = "^[0-9]{2,3}/[0-9]{2,3}$", message = "Blood pressure must follow SYS/DIA format (e.g. 120/80)")
        String bloodPressure,

        @Min(value = 30, message = "Heart rate must be at least 30 bpm")
        @Max(value = 300, message = "Heart rate cannot exceed 300 bpm")
        Integer heartRate,

        @DecimalMin(value = "30.0", message = "Temperature must be at least 30.0 °C")
        @DecimalMax(value = "45.0", message = "Temperature cannot exceed 45.0 °C")
        BigDecimal temperature,

        @DecimalMin(value = "0.5", message = "Weight must be at least 0.5 kg")
        @DecimalMax(value = "500.0", message = "Weight cannot exceed 500.0 kg")
        BigDecimal weight,

        @Size(max = 2000, message = "Observations cannot exceed 2000 characters")
        String observations
) {}
