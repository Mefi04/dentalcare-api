package com.dentalcare.api.modules.auth.mapper;

import com.dentalcare.api.modules.users.model.Permission;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUserMapperTests {
    private final AuthUserMapper mapper = new AuthUserMapper();

    @Test
    void mapsActiveRolesAndPermissionsToDistinctSortedAuthorities() {
        Role administrator = role("ADMINISTRATOR", true,
                Set.of(permission("PATIENT_READ"), permission("PATIENT_READ"), permission("  ")));
        Role inactive = role("DENTIST", false, Set.of(permission("APPOINTMENT_READ")));
        Role blankRole = role(" ", true, Set.of());
        User user = userWithRoles(administrator, inactive, blankRole);

        assertThat(mapper.authorities(user))
                .containsExactly("PATIENT_READ", "ROLE_ADMINISTRATOR");
    }

    @Test
    void userResponseKeepsPersistedCodesWithoutRolePrefix() {
        User user = userWithRoles(role("ADMINISTRATOR", true, Set.of(permission("PATIENT_READ"))));

        var response = mapper.toResponse(user);

        assertThat(response.roles()).containsExactly("ADMINISTRATOR");
        assertThat(response.permissions()).containsExactly("PATIENT_READ");
    }

    private static User userWithRoles(Role... roles) {
        User user = new User(UUID.randomUUID(), "user", "user@example.com", "hash", UserStatus.ACTIVE,
                Instant.now(), Instant.now());
        user.setRoles(Set.of(roles));
        return user;
    }

    private static Role role(String code, boolean active, Set<Permission> permissions) {
        Role role = new Role(UUID.randomUUID(), code, code, null, active);
        role.setPermissions(permissions);
        return role;
    }

    private static Permission permission(String code) {
        return new Permission(UUID.randomUUID(), code, null);
    }
}
