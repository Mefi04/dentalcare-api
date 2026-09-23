package com.dentalcare.api.modules.users.dto.request;

import com.dentalcare.api.modules.users.model.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull(message = "Status is required") UserStatus status) {
}
