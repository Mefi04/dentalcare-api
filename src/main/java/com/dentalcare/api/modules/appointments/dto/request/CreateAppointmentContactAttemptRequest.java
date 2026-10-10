package com.dentalcare.api.modules.appointments.dto.request;

import com.dentalcare.api.modules.appointments.model.AppointmentContactResult;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateAppointmentContactAttemptRequest(
        @NotNull AppointmentContactResult result,
        @Size(max = 500) String observation,
        @Future Instant nextAttemptAt) { }
