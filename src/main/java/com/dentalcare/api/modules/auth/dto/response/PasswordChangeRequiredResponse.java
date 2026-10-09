package com.dentalcare.api.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordChangeRequiredResponse(
        @Schema(example = "true") boolean requiresPasswordChange,
        String passwordChangeToken,
        InitialPasswordChangeUserResponse user) {
}
