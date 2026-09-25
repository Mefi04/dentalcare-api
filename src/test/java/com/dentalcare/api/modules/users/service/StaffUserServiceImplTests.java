package com.dentalcare.api.modules.users.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.users.dto.request.CreateStaffUserRequest;
import com.dentalcare.api.modules.users.dto.request.UpdateStaffUserRequest;
import com.dentalcare.api.modules.users.dto.request.UpdateUserStatusRequest;
import com.dentalcare.api.modules.users.mapper.StaffUserMapper;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffUserServiceImplTests {
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder encoder;
    private StaffUserServiceImpl service;
    private Role secretary;

    @BeforeEach void setUp() {
        service = new StaffUserServiceImpl(users, roles, new StaffUserMapper(), encoder,
                Clock.fixed(NOW, ZoneOffset.UTC));
        secretary = new Role(UUID.randomUUID(), "SECRETARY", "Secretaría", null, true);
    }

    @Test void createsPendingUserWithNormalizedDataAndHashedTemporaryPassword() {
        when(roles.findByCode("SECRETARY")).thenReturn(Optional.of(secretary));
        when(encoder.encode(anyString())).thenReturn("$2a$hash");
        when(users.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(new CreateStaffUserRequest("  Laura Ramírez ", "1234 56789 0123",
                " LAURA@DentalCare.GT ", "secretary"));

        assertThat(result.temporaryPassword()).hasSize(16);
        assertThat(result.user().fullName()).isEqualTo("Laura Ramírez");
        assertThat(result.user().username()).startsWith("staff-").hasSize(42);
        assertThat(result.user().email()).isEqualTo("laura@dentalcare.gt");
        assertThat(result.user().status()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(result.user().roles()).containsExactly("SECRETARY");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCui()).isEqualTo("1234567890123");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo(result.temporaryPassword());
        verify(encoder).encode(result.temporaryPassword());
    }

    @Test void rejectsDuplicateCuiAndEmail() {
        when(roles.findByCode("SECRETARY")).thenReturn(Optional.of(secretary));
        when(users.existsByCui("1234567890123")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ConflictException.class).hasMessageContaining("CUI");

        when(users.existsByCui("1234567890123")).thenReturn(false);
        when(users.existsByEmail("laura@dentalcare.gt")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ConflictException.class).hasMessageContaining("email");
    }

    @Test void rejectsMissingAndInactiveRole() {
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ResourceNotFoundException.class);
        secretary.setActive(false);
        when(roles.findByCode("SECRETARY")).thenReturn(Optional.of(secretary));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ConflictException.class).hasMessage("Role is inactive");
    }

    @Test void staffEmailRemainsRequiredWhenPatientAccountsAllowNullEmail() {
        assertThatThrownBy(() -> service.create(new CreateStaffUserRequest("Laura", "1234567890123", null, "SECRETARY")))
                .isInstanceOf(BadRequestException.class).hasMessage("Email must be valid");
    }

    @Test void searchDelegatesAllFiltersToRepositoryAndCapsPageSize() {
        when(users.searchStaffUsers(eq("laura"), eq(UserStatus.ACTIVE), eq("SECRETARY"),
                eq(StaffUserServiceImpl.OFFICIAL_ROLES), any()))
                .thenReturn(new PageImpl<>(List.of()));
        service.search(1, 500, " laura ", UserStatus.ACTIVE, "secretary");
        verify(users).searchStaffUsers(eq("laura"), eq(UserStatus.ACTIVE), eq("SECRETARY"),
                eq(StaffUserServiceImpl.OFFICIAL_ROLES),
                argThat(page -> page.getPageNumber() == 1 && page.getPageSize() == 100));
    }

    @Test void patientRoleIsInvalidAsSearchFilter() {
        assertThatThrownBy(() -> service.search(0, 20, null, null, "PATIENT"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid role filter");
        verifyNoInteractions(users);
    }

    @Test void findByIdTreatsPatientAsUnavailable() {
        User patient = patient();
        when(users.findWithRolesById(patient.getId())).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.findById(patient.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test void findByIdReturnsStaffUser() {
        User staff = user(UserStatus.ACTIVE);
        when(users.findWithRolesById(staff.getId())).thenReturn(Optional.of(staff));

        assertThat(service.findById(staff.getId()).id()).isEqualTo(staff.getId());
    }

    @Test void findByIdTreatsUnknownUserAsUnavailable() {
        UUID id = UUID.randomUUID();
        when(users.findWithRolesById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test void updateChangesOnlyEditableFields() {
        User user = user(UserStatus.ACTIVE);
        String username = user.getUsername(); String cui = user.getCui();
        Role dentist = new Role(UUID.randomUUID(), "DENTIST", "Odontólogo", null, true);
        when(users.findWithRolesById(user.getId())).thenReturn(Optional.of(user));
        when(roles.findByCode("DENTIST")).thenReturn(Optional.of(dentist));
        when(users.saveAndFlush(user)).thenReturn(user);

        var result = service.update(user.getId(), new UpdateStaffUserRequest(" Nueva ", " NEW@MAIL.COM ", "DENTIST"));
        assertThat(result.fullName()).isEqualTo("Nueva");
        assertThat(result.email()).isEqualTo("new@mail.com");
        assertThat(result.roles()).containsExactly("DENTIST");
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getCui()).isEqualTo(cui);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test void updateRejectsPatientWithoutChangingData() {
        User patient = patient();
        String originalName = patient.getFullName();
        String originalEmail = patient.getEmail();
        Set<Role> originalRoles = patient.getRoles();
        when(users.findWithRolesById(patient.getId())).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.update(patient.getId(),
                new UpdateStaffUserRequest("Changed", "changed@example.com", "DENTIST")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
        assertThat(patient.getFullName()).isEqualTo(originalName);
        assertThat(patient.getEmail()).isEqualTo(originalEmail);
        assertThat(patient.getRoles()).isSameAs(originalRoles);
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(roles);
    }

    @Test void statusTransitionsWorkButPendingCannotBeActivated() {
        User user = user(UserStatus.ACTIVE);
        when(users.findWithRolesById(user.getId())).thenReturn(Optional.of(user));
        when(users.saveAndFlush(user)).thenReturn(user);
        assertThat(service.updateStatus(user.getId(), new UpdateUserStatusRequest(UserStatus.INACTIVE)).status())
                .isEqualTo(UserStatus.INACTIVE);
        assertThat(service.updateStatus(user.getId(), new UpdateUserStatusRequest(UserStatus.ACTIVE)).status())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(service.updateStatus(user.getId(), new UpdateUserStatusRequest(UserStatus.LOCKED)).status())
                .isEqualTo(UserStatus.LOCKED);
        assertThat(service.updateStatus(user.getId(), new UpdateUserStatusRequest(UserStatus.ACTIVE)).status())
                .isEqualTo(UserStatus.ACTIVE);
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        assertThatThrownBy(() -> service.updateStatus(user.getId(), new UpdateUserStatusRequest(UserStatus.ACTIVE)))
                .isInstanceOf(ConflictException.class);
    }

    @Test void statusUpdateRejectsPatientWithoutChangingStatus() {
        User patient = patient();
        UserStatus originalStatus = patient.getStatus();
        when(users.findWithRolesById(patient.getId())).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.updateStatus(patient.getId(),
                new UpdateUserStatusRequest(UserStatus.INACTIVE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
        assertThat(patient.getStatus()).isEqualTo(originalStatus);
        verify(users, never()).saveAndFlush(any());
    }

    @Test void activeRoleCatalogExcludesUnknownCodesDefensively() {
        Role invented = new Role(UUID.randomUUID(), "INVENTED", "Inventado", null, true);
        when(roles.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(secretary, invented));
        assertThat(service.findActiveRoles()).extracting("code").containsExactly("SECRETARY");
    }

    private CreateStaffUserRequest request() {
        return new CreateStaffUserRequest("Laura", "1234567890123", "laura@dentalcare.gt", "SECRETARY");
    }
    private User user(UserStatus status) {
        User u = new User(UUID.randomUUID(), "staff-" + UUID.randomUUID(), "Laura", "laura@dentalcare.gt",
                "1234567890123", "hash", status, NOW.minusSeconds(10), NOW.minusSeconds(10));
        u.setRoles(Set.of(secretary)); return u;
    }

    private User patient() {
        User patient = new User(UUID.randomUUID(), "patient-" + UUID.randomUUID(), "Patient", null,
                "9876543210123", "hash", UserStatus.ACTIVE, NOW.minusSeconds(10), NOW.minusSeconds(10));
        patient.setRoles(Set.of(new Role(UUID.randomUUID(), "PATIENT", "Paciente", null, true)));
        return patient;
    }
}
