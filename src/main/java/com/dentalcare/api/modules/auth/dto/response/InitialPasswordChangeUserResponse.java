package com.dentalcare.api.modules.auth.dto.response;

import java.util.UUID;

public record InitialPasswordChangeUserResponse(UUID id, String fullName) {
}
