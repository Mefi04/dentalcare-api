package com.dentalcare.api.modules.appointments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contact details supplied for a public request; visible only to authorized clinic staff")
public record PublicAppointmentRequesterResponse(
        String fullName,
        String cui,
        String phone,
        String email,
        String reason) {
}
