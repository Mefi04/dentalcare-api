package com.dentalcare.api.modules.appointments.dto.request;
import jakarta.validation.constraints.NotNull;
public record PublicVerificationChannelRequest(@NotNull Channel channel) {
    public enum Channel { SMS, EMAIL }
}
