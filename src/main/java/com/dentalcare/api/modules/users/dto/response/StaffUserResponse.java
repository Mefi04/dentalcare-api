package com.dentalcare.api.modules.users.dto.response;

import com.dentalcare.api.modules.users.model.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffUserResponse(UUID id, String fullName, String username, String email, UserStatus status,
                                List<String> roles, Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
}
