package com.dentalcare.api.modules.auth.dto.response;

import com.dentalcare.api.modules.users.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record ActivateAccountResponse(
        @Schema(description = "Account status after activation", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "Success message", example = "Account activated successfully")
        String message) {
}
