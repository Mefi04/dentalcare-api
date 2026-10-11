package com.dentalcare.api.modules.appointments.dto.request;

import com.dentalcare.api.modules.appointments.model.AppointmentContactResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Reception contact attempt record for an appointment request")
public record CreateContactAttemptRequest(
        @NotNull
        @Schema(description = "Outcome of the contact attempt", example = "CONTACTED")
        AppointmentContactResult result,

        @Size(max = 500)
        @Schema(description = "Notes regarding the call or conversation", example = "El paciente confirmó disponibilidad para el martes por la tarde.")
        String notes
) {
}
