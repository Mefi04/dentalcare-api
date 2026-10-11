package com.dentalcare.api.modules.assistant.dto.response;

import com.dentalcare.api.modules.appointments.dto.response.AppointmentSlotResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Response returned by the public virtual assistant")
public record PublicAssistantResponse(
        @Schema(description = "Assistant textual reply", example = "Contamos con disponibilidad el día solicitado. Por favor selecciona tu horario preferido.")
        String reply,

        @Schema(description = "Suggested available appointment slots, if availability was consulted")
        List<AppointmentSlotResponse> suggestedSlots,

        @Schema(description = "Recommended UI action: GUIDE_TO_INTAKE, PROVIDE_INFORMATION, NONE", example = "GUIDE_TO_INTAKE")
        String action
) {
}
