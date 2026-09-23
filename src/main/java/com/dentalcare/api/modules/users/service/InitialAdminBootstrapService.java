package com.dentalcare.api.modules.users.service;

import com.dentalcare.api.config.InitialAdminProperties;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class InitialAdminBootstrapService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAdminBootstrapService.class);
    private static final String ADMINISTRATOR = "ADMINISTRATOR";
    private static final int MAX_FULL_NAME_LENGTH = 150;
    private static final int MAX_EMAIL_LENGTH = 255;

    private final InitialAdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public InitialAdminBootstrapService(InitialAdminProperties properties,
                                        UserRepository userRepository,
                                        RoleRepository roleRepository,
                                        PasswordEncoder passwordEncoder,
                                        Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void bootstrap() {
        if (!properties.isEnabled()) {
            return;
        }

        Role administratorRole = roleRepository.findByCodeForUpdate(ADMINISTRATOR)
                .orElseThrow(() -> new InitialAdminBootstrapException("Initial administrator role is not configured"));
        if (!administratorRole.isActive()) {
            throw new InitialAdminBootstrapException("Initial administrator role is inactive");
        }

        if (userRepository.existsByRoles_Code(ADMINISTRATOR)) {
            LOGGER.info("Initial administrator bootstrap skipped because an administrator already exists");
            return;
        }

        String fullName = required(properties.getFullName(), "Initial administrator full name is required");
        if (fullName.length() > MAX_FULL_NAME_LENGTH) {
            throw new InitialAdminBootstrapException("Initial administrator full name exceeds the allowed length");
        }
        String cui = normalizeCui(properties.getCui());
        String email = normalizeEmail(properties.getEmail());
        String password = required(properties.getPassword(), "Initial administrator password is required");

        if (userRepository.existsByCui(cui)) {
            throw new InitialAdminBootstrapException("Initial administrator CUI is already in use");
        }
        if (userRepository.existsByEmail(email)) {
            throw new InitialAdminBootstrapException("Initial administrator email is already in use");
        }

        Instant now = clock.instant();
        User administrator = new User(UUID.randomUUID(), generateUsername(), fullName, email, cui,
                passwordEncoder.encode(password), UserStatus.ACTIVE, now, now);
        administrator.setRoles(Set.of(administratorRole));
        save(administrator);
        LOGGER.info("Initial administrator bootstrap completed");
    }

    private void save(User administrator) {
        try {
            userRepository.saveAndFlush(administrator);
        } catch (DataIntegrityViolationException exception) {
            throw new InitialAdminBootstrapException("Initial administrator could not be created due to a data conflict");
        }
    }

    private String generateUsername() {
        String username;
        do {
            username = "staff-" + UUID.randomUUID();
        } while (userRepository.existsByUsername(username));
        return username;
    }

    private String normalizeCui(String input) {
        String value = optional(input);
        if (value == null || !value.matches("[0-9 ]+")) {
            throw new InitialAdminBootstrapException("Initial administrator CUI must contain exactly 13 digits");
        }
        value = value.replace(" ", "");
        if (!value.matches("\\d{13}")) {
            throw new InitialAdminBootstrapException("Initial administrator CUI must contain exactly 13 digits");
        }
        return value;
    }

    private String normalizeEmail(String input) {
        String value = optional(input);
        if (value == null || value.length() > MAX_EMAIL_LENGTH
                || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InitialAdminBootstrapException("Initial administrator email must be valid");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String required(String input, String message) {
        String value = optional(input);
        if (value == null) {
            throw new InitialAdminBootstrapException(message);
        }
        return value;
    }

    private String optional(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        return value.isEmpty() ? null : value;
    }
}
