package com.dentalcare.api.modules.appointments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAppointmentConversationMessageRequest(
        @NotBlank @Size(max = 500) String text) {}
