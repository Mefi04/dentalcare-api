package com.dentalcare.api.modules.users.service;

import com.dentalcare.api.config.InitialAdminProperties;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialAdminBootstrapServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Mock private UserRepository users;
    @Mock private RoleRepository roles;
    @Mock private PasswordEncoder encoder;

    private InitialAdminProperties properties;
    private InitialAdminBootstrapService service;
    private Role administrator;

    @BeforeEach
    void setUp() {
        properties = validProperties();
        service = new InitialAdminBootstrapService(properties, users, roles, encoder,
                Clock.fixed(NOW, ZoneOffset.UTC));
        administrator = new Role(UUID.randomUUID(), "ADMINISTRATOR", "Administrator", null, true);
    }

    @Test
    void skipsWithoutQueryingRepositoriesWhenDisabled() {
        properties.setEnabled(false);

        service.bootstrap();

        verifyNoInteractions(users, roles, encoder);
    }

    @Test
    void createsOneActiveAdministratorWithNormalizedDataAndEncodedPassword() {
        prepareCreatableAdministrator();
        when(encoder.encode("safe bootstrap password")).thenReturn("$2a$encoded-hash");
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.bootstrap();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).startsWith("staff-").hasSize(42);
        assertThat(saved.getFullName()).isEqualTo("Initial Administrator");
        assertThat(saved.getCui()).isEqualTo("1234567890123");
        assertThat(saved.getEmail()).isEqualTo("admin@example.test");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$encoded-hash").isNotEqualTo("safe bootstrap password");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getRoles()).containsExactly(administrator);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        verify(encoder).encode("safe bootstrap password");
    }

    @Test
    void skipsSecondExecutionWhenAdministratorAlreadyExistsAfterLockingRole() {
        when(roles.findByCodeForUpdate("ADMINISTRATOR")).thenReturn(Optional.of(administrator));
        when(users.existsByRoles_Code("ADMINISTRATOR")).thenReturn(true);

        service.bootstrap();

        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void checksForExistingAdministratorOnlyAfterAcquiringRoleLock() {
        prepareCreatableAdministrator();
        when(encoder.encode(any())).thenReturn("$2a$encoded-hash");
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.bootstrap();

        InOrder order = org.mockito.Mockito.inOrder(roles, users);
        order.verify(roles).findByCodeForUpdate("ADMINISTRATOR");
        order.verify(users).existsByRoles_Code("ADMINISTRATOR");
    }

    @Test
    void rejectsMissingOrInactiveAdministratorRole() {
        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(InitialAdminBootstrapException.class)
                .hasMessage("Initial administrator role is not configured");
        verify(users, never()).saveAndFlush(any());

        administrator.setActive(false);
        when(roles.findByCodeForUpdate("ADMINISTRATOR")).thenReturn(Optional.of(administrator));
        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(InitialAdminBootstrapException.class)
                .hasMessage("Initial administrator role is inactive");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateCuiAndEmailWithoutSaving() {
        prepareCreatableAdministrator();
        when(users.existsByCui("1234567890123")).thenReturn(true);
        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(InitialAdminBootstrapException.class)
                .hasMessageContaining("CUI");

        when(users.existsByCui("1234567890123")).thenReturn(false);
        when(users.existsByEmail("admin@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(InitialAdminBootstrapException.class)
                .hasMessageContaining("email");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidConfigurationWithoutLeakingPassword() {
        assertInvalid("   ", "1234567890123", "admin@example.test", "safe bootstrap password", "full name");
        assertInvalid("Administrator", "123", "admin@example.test", "safe bootstrap password", "CUI");
        assertInvalid("Administrator", "1234567890123", "not-an-email", "safe bootstrap password", "email");
        assertInvalid("Administrator", "1234567890123", "admin@example.test", "   ", "password");
        assertInvalid("x".repeat(151), "1234567890123", "admin@example.test", "safe bootstrap password", "length");
        assertInvalid("Administrator", "1234567890123", "a".repeat(245) + "@example.test",
                "safe bootstrap password", "email");
    }

    @Test
    void translatesDatabaseConflictsToSafeBootstrapFailure() {
        prepareCreatableAdministrator();
        when(encoder.encode(any())).thenReturn("$2a$encoded-hash");
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("database constraint details"));

        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(InitialAdminBootstrapException.class)
                .hasMessage("Initial administrator could not be created due to a data conflict")
                .hasNoCause();
    }

    private void assertInvalid(String fullName, String cui, String email, String password, String expectedMessage) {
        properties.setFullName(fullName);
        properties.setCui(cui);
        properties.setEmail(email);
        properties.setPassword(password);
        when(roles.findByCodeForUpdate("ADMINISTRATOR")).thenReturn(Optional.of(administrator));

        assertThatThrownBy(() -> service.bootstrap())
                .isInstanceOf(InitialAdminBootstrapException.class)
                .hasMessageContaining(expectedMessage)
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("safe bootstrap password"));
        verify(users, never()).saveAndFlush(any());
    }

    private void prepareCreatableAdministrator() {
        when(roles.findByCodeForUpdate("ADMINISTRATOR")).thenReturn(Optional.of(administrator));
        when(users.existsByRoles_Code("ADMINISTRATOR")).thenReturn(false);
    }

    private InitialAdminProperties validProperties() {
        InitialAdminProperties result = new InitialAdminProperties();
        result.setEnabled(true);
        result.setFullName("  Initial Administrator  ");
        result.setCui("1234 56789 0123");
        result.setEmail(" ADMIN@EXAMPLE.TEST ");
        result.setPassword("safe bootstrap password");
        return result;
    }
}
