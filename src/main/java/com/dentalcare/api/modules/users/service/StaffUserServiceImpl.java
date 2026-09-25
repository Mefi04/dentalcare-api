package com.dentalcare.api.modules.users.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.users.dto.request.CreateStaffUserRequest;
import com.dentalcare.api.modules.users.dto.request.UpdateStaffUserRequest;
import com.dentalcare.api.modules.users.dto.request.UpdateUserStatusRequest;
import com.dentalcare.api.modules.users.dto.response.CreateStaffUserResponse;
import com.dentalcare.api.modules.users.dto.response.RoleResponse;
import com.dentalcare.api.modules.users.dto.response.StaffUserResponse;
import com.dentalcare.api.modules.users.mapper.StaffUserMapper;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.List;

@Service
public class StaffUserServiceImpl implements StaffUserService {
    static final Set<String> OFFICIAL_ROLES = Set.of("ADMINISTRATOR", "SECRETARY", "DENTIST", "ASSISTANT", "CASHIER");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int TEMPORARY_PASSWORD_LENGTH = 16;
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StaffUserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @org.springframework.beans.factory.annotation.Autowired
    public StaffUserServiceImpl(UserRepository userRepository, RoleRepository roleRepository, StaffUserMapper mapper,
                                PasswordEncoder passwordEncoder, Clock clock) {
        this(userRepository, roleRepository, mapper, passwordEncoder, clock, new SecureRandom());
    }

    StaffUserServiceImpl(UserRepository userRepository, RoleRepository roleRepository, StaffUserMapper mapper,
                         PasswordEncoder passwordEncoder, Clock clock, SecureRandom secureRandom) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    @Transactional
    public CreateStaffUserResponse create(CreateStaffUserRequest request) {
        String fullName = required(request.fullName(), "Full name is required");
        String cui = normalizeCui(request.cui());
        String email = normalizeEmail(request.email());
        Role role = resolveActiveRole(request.roleCode());
        if (userRepository.existsByCui(cui)) throw new ConflictException("A user with this CUI already exists");
        if (userRepository.existsByEmail(email)) throw new ConflictException("A user with this email already exists");

        String username = generateUsername();
        String temporaryPassword = generateTemporaryPassword();
        var now = clock.instant();
        User user = new User(UUID.randomUUID(), username, fullName, email, cui,
                passwordEncoder.encode(temporaryPassword), UserStatus.PENDING_ACTIVATION, now, now);
        user.setRoles(Set.of(role));
        return new CreateStaffUserResponse(mapper.toResponse(save(user)), temporaryPassword);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StaffUserResponse> search(int page, int size, String search, UserStatus status, String role) {
        if (page < 0) throw new BadRequestException("Page must not be negative");
        if (size < 1) throw new BadRequestException("Size must be at least 1");
        String roleCode = optionalUpper(role);
        if (roleCode != null && !OFFICIAL_ROLES.contains(roleCode)) throw new BadRequestException("Invalid role filter");
        return userRepository.searchStaffUsers(optional(search), status, roleCode, OFFICIAL_ROLES,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public StaffUserResponse findById(UUID id) {
        return mapper.toResponse(findUser(id));
    }

    @Override
    @Transactional
    public StaffUserResponse update(UUID id, UpdateStaffUserRequest request) {
        User user = findUser(id);
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("A user with this email already exists");
        }
        user.setFullName(required(request.fullName(), "Full name is required"));
        user.setEmail(email);
        user.setRoles(Set.of(resolveActiveRole(request.roleCode())));
        user.setUpdatedAt(clock.instant());
        return mapper.toResponse(save(user));
    }

    @Override
    @Transactional
    public StaffUserResponse updateStatus(UUID id, UpdateUserStatusRequest request) {
        User user = findUser(id);
        UserStatus target = request.status();
        if (target == null) throw new BadRequestException("Status is required");
        if (target == UserStatus.PENDING_ACTIVATION) {
            throw new ConflictException("Pending activation status cannot be assigned manually");
        }
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            throw new ConflictException("Pending accounts must complete the activation flow");
        }
        user.setStatus(target);
        user.setUpdatedAt(clock.instant());
        return mapper.toResponse(save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> findActiveRoles() {
        return roleRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .filter(role -> OFFICIAL_ROLES.contains(role.getCode()))
                .map(mapper::toResponse).toList();
    }

    private User findUser(UUID id) {
        User user = userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRoles().stream().noneMatch(role -> OFFICIAL_ROLES.contains(role.getCode()))) {
            throw new ResourceNotFoundException("User not found");
        }
        return user;
    }

    private Role resolveActiveRole(String input) {
        String code = optionalUpper(input);
        if (code == null || !OFFICIAL_ROLES.contains(code)) {
            throw new ResourceNotFoundException("Role not found");
        }
        Role role = roleRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        if (!role.isActive()) throw new ConflictException("Role is inactive");
        return role;
    }

    private User save(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("User data conflicts with an existing account");
        }
    }

    private String generateUsername() {
        String username;
        do username = "staff-" + UUID.randomUUID();
        while (userRepository.existsByUsername(username));
        return username;
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(secureRandom.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    private String normalizeCui(String input) {
        String value = optional(input);
        if (value == null || !value.matches("[0-9 ]+")) throw new BadRequestException("CUI must contain exactly 13 digits");
        value = value.replace(" ", "");
        if (!value.matches("\\d{13}")) throw new BadRequestException("CUI must contain exactly 13 digits");
        return value;
    }

    private String normalizeEmail(String input) {
        String value = optional(input);
        if (value == null || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new BadRequestException("Email must be valid");
        return value.toLowerCase(Locale.ROOT);
    }

    private String required(String input, String message) {
        String value = optional(input);
        if (value == null) throw new BadRequestException(message);
        return value;
    }

    private String optionalUpper(String input) {
        String value = optional(input);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String optional(String input) {
        if (input == null) return null;
        String value = input.trim();
        return value.isEmpty() ? null : value;
    }
}
