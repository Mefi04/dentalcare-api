package com.dentalcare.api.modules.appointments.dto.request;

import com.dentalcare.api.modules.appointments.model.WaitingRoomStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateWaitingRoomStatusRequest(@NotNull WaitingRoomStatus status) {
}
