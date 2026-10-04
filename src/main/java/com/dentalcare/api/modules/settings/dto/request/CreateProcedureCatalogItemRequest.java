package com.dentalcare.api.modules.settings.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateProcedureCatalogItemRequest(
        @NotBlank @Size(max=50) String code,
        @NotBlank @Size(max=200) String name,
        @NotBlank @Size(max=100) String category,
        @NotNull @Min(5) @Max(480) Integer durationMinutes,
        @NotNull @DecimalMin(value="0.01") @Digits(integer=10, fraction=2) BigDecimal basePrice) {}
