package com.dentalcare.api.modules.users.repository;

import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryIntegrationTests {

    private static final Set<String> STAFF_ROLES = Set.of(
            "ADMINISTRATOR", "SECRETARY", "DENTIST", "ASSISTANT", "CASHIER");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        Role administrator = role("ADMINISTRATOR", "Administrador");
        Role secretary = role("SECRETARY", "Secretaría");
        Role patient = role("PATIENT", "Paciente");

        userRepository.save(user("admin-user", "Coincide Admin", "admin@example.com",
                "1000000000001", UserStatus.ACTIVE, administrator));
        userRepository.save(user("secretary-user", "Secretaria", "secretary@example.com",
                "1000000000002", UserStatus.INACTIVE, secretary));
        userRepository.save(user("patient-user", "Coincide Patient", "patient@example.com",
                "1000000000003", UserStatus.ACTIVE, patient));
        userRepository.flush();
    }

    @Test
    void returnsOnlyUsersWithOfficialStaffRoles() {
        Page<User> result = search(null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(User::getUsername)
                .containsExactlyInAnyOrder("admin-user", "secretary-user")
                .doesNotContain("patient-user");
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    void textSearchStillExcludesMatchingPatient() {
        Page<User> result = search("Coincide", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(User::getUsername)
                .containsExactly("admin-user");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void statusFilterStillExcludesPatientWithMatchingStatus() {
        Page<User> result = search(null, UserStatus.ACTIVE, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(User::getUsername)
                .containsExactly("admin-user");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void staffRoleFilterReturnsOnlyRequestedStaffRole() {
        Page<User> result = search(null, null, "SECRETARY", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(User::getUsername)
                .containsExactly("secretary-user");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void patientIsExcludedBeforePaginationAndCountQuery() {
        PageRequest firstPage = PageRequest.of(0, 1, Sort.by("username"));
        PageRequest secondPage = PageRequest.of(1, 1, Sort.by("username"));

        Page<User> first = search(null, null, null, firstPage);
        Page<User> second = search(null, null, null, secondPage);

        assertThat(first.getContent()).extracting(User::getUsername).containsExactly("admin-user");
        assertThat(second.getContent()).extracting(User::getUsername).containsExactly("secretary-user");
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(second.getTotalElements()).isEqualTo(2);
        assertThat(second.getTotalPages()).isEqualTo(2);
    }

    private Page<User> search(String text, UserStatus status, String role, PageRequest pageRequest) {
        return userRepository.searchStaffUsers(text, status, role, STAFF_ROLES, pageRequest);
    }

    private Role role(String code, String name) {
        return roleRepository.findByCode(code)
                .orElseGet(() -> roleRepository.save(new Role(UUID.randomUUID(), code, name, null, true)));
    }

    private User user(String username, String fullName, String email, String cui,
                      UserStatus status, Role role) {
        Instant now = Instant.parse("2026-09-25T12:00:00Z");
        User user = new User(UUID.randomUUID(), username, fullName, email, cui, "hash", status, now, now);
        user.setRoles(Set.of(role));
        return user;
    }
}
