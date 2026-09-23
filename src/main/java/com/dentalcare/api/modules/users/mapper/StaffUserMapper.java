package com.dentalcare.api.modules.users.mapper;

import com.dentalcare.api.modules.users.dto.response.RoleResponse;
import com.dentalcare.api.modules.users.dto.response.StaffUserResponse;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.stereotype.Component;

@Component
public class StaffUserMapper {
    public StaffUserResponse toResponse(User user) {
        return new StaffUserResponse(user.getId(), user.getFullName(), user.getUsername(), user.getEmail(),
                user.getStatus(), user.getRoles().stream().map(Role::getCode).sorted().toList(),
                user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getCode(), role.getName());
    }
}
