package com.dentalcare.api.modules.sterilization.dto.request;
import com.dentalcare.api.modules.sterilization.model.SterilizationMethod;
import jakarta.validation.constraints.*;
public record CreateSterilizationProtocolRequest(
 @NotBlank @Size(max=150) String name,
 @NotNull SterilizationMethod method,
 @Size(max=500) String description,
 @NotBlank @Size(max=2000) String instructions) {}
