package com.dentalcare.api.modules.sterilization.dto.request;
import com.dentalcare.api.modules.sterilization.model.SterilizationCycleStatus;
import jakarta.validation.constraints.NotNull;
public record UpdateSterilizationCycleStatusRequest(@NotNull SterilizationCycleStatus status) {}
