package com.dentalcare.api.modules.appointments.dto.request;
import jakarta.validation.constraints.NotNull;
public record PublicAppointmentDecisionRequest(@NotNull Decision decision) {
    public enum Decision { ACCEPT, REJECT }
}
