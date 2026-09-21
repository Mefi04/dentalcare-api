package com.dentalcare.api.modules.auth.mapper;

import com.dentalcare.api.modules.auth.dto.response.UserResponse;
import com.dentalcare.api.modules.users.model.Permission;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthUserMapper {
    private static final String ROLE_PREFIX = "ROLE_";

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getStatus(),
                roleCodes(user), permissionCodes(user));
    }

    public List<String> authorities(User user) {
        return user.getRoles().stream().filter(Role::isActive)
                .flatMap(role -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(role.getCode())
                                .filter(code -> code != null && !code.isBlank())
                                .map(code -> ROLE_PREFIX + code),
                        role.getPermissions().stream().map(Permission::getCode)))
                .filter(code -> code != null && !code.isBlank()).distinct().sorted().toList();
    }

    private List<String> roleCodes(User user) {
        return user.getRoles().stream().filter(Role::isActive).map(Role::getCode)
                .filter(code -> code != null && !code.isBlank()).distinct().sorted().toList();
    }

    private List<String> permissionCodes(User user) {
        return user.getRoles().stream().filter(Role::isActive).flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode).filter(code -> code != null && !code.isBlank()).distinct().sorted().toList();
    }
}
