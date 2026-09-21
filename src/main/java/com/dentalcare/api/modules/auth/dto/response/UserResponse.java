package com.dentalcare.api.modules.auth.dto.response;

import com.dentalcare.api.modules.users.model.UserStatus;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        UserStatus status,
        List<String> roles,
        List<String> permissions) {
}
