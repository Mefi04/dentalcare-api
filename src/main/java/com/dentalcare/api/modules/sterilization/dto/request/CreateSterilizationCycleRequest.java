package com.dentalcare.api.modules.sterilization.dto.request;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;
public record CreateSterilizationCycleRequest(
 @NotNull UUID protocolId,
 @NotEmpty Set<@NotNull UUID> instrumentIds,
 @NotBlank @Size(max=500) String observations) {}
