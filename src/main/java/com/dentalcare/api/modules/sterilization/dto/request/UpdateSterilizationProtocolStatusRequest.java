package com.dentalcare.api.modules.sterilization.dto.request;
import jakarta.validation.constraints.NotNull;
public record UpdateSterilizationProtocolStatusRequest(@NotNull Boolean active) {}
