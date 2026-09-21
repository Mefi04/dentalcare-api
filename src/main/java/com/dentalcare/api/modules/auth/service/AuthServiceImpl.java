package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.auth.dto.request.LoginRequest;
import com.dentalcare.api.modules.auth.dto.response.LoginResponse;
import com.dentalcare.api.modules.auth.dto.response.UserResponse;
import com.dentalcare.api.modules.auth.mapper.AuthUserMapper;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.security.jwt.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthUserMapper mapper;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, AuthUserMapper mapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = User.normalize(request.identifier());
        User user = userRepository.findWithRolesAndPermissionsByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        String token = jwtService.createAccessToken(user.getId(), mapper.authorities(user));
        return new LoginResponse(token, "Bearer", jwtService.getAccessTokenLifetimeSeconds(), mapper.toResponse(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findWithRolesAndPermissionsById(userId)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
        return mapper.toResponse(user);
    }
}
