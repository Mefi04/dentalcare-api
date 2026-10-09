package com.dentalcare.api.modules.appointments.dto.request;
import jakarta.validation.constraints.NotNull;
public record ClinicSchedulingMessageRequest(@NotNull Type type) {
    public enum Type { RECEPTION_FOLLOW_UP, REQUEST_RECEIVED }
}
