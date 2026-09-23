package com.dentalcare.api.modules.users.dto.response;

public record CreateStaffUserResponse(StaffUserResponse user, String temporaryPassword) {
}
