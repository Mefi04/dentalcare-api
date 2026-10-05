package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.auth.config.PasswordRecoveryProperties;
import com.dentalcare.api.modules.auth.dto.request.ConfirmPasswordRecoveryRequest;
import com.dentalcare.api.modules.auth.dto.request.PasswordRecoveryRequest;
import com.dentalcare.api.modules.auth.dto.response.PasswordRecoveryResponse;
import com.dentalcare.api.modules.auth.model.PasswordRecoveryToken;
import com.dentalcare.api.modules.auth.repository.PasswordRecoveryTokenRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.shared.validation.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {
    @org.springframework.beans.factory.annotation.Autowired(required = false) private AuditService auditService;

    static final String REQUEST_MESSAGE =
            "If the account is eligible, a recovery code will be sent";
    static final String INVALID_RECOVERY = "Invalid or expired recovery credentials";
    static final String SUCCESS_MESSAGE = "Password updated successfully";

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordRecoveryServiceImpl.class);
    private static final int CODE_BOUND = 100_000_000;

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordRecoveryTokenRepository tokenRepository;
    private final PasswordRecoveryCodeDelivery codeDelivery;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordRecoveryProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @org.springframework.beans.factory.annotation.Autowired
    public PasswordRecoveryServiceImpl(UserRepository userRepository,
                                       PatientRepository patientRepository,
                                       PasswordRecoveryTokenRepository tokenRepository,
                                       PasswordRecoveryCodeDelivery codeDelivery,
                                       RefreshTokenService refreshTokenService,
                                       PasswordEncoder passwordEncoder,
                                       PasswordRecoveryProperties properties,
                                       Clock clock) {
        this(userRepository, patientRepository, tokenRepository, codeDelivery, refreshTokenService,
                passwordEncoder, properties, clock, new SecureRandom());
    }

    PasswordRecoveryServiceImpl(UserRepository userRepository,
                                PatientRepository patientRepository,
                                PasswordRecoveryTokenRepository tokenRepository,
                                PasswordRecoveryCodeDelivery codeDelivery,
                                RefreshTokenService refreshTokenService,
                                PasswordEncoder passwordEncoder,
                                PasswordRecoveryProperties properties,
                                Clock clock,
                                SecureRandom secureRandom) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.tokenRepository = tokenRepository;
        this.codeDelivery = codeDelivery;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    @Transactional
    public PasswordRecoveryResponse requestRecovery(PasswordRecoveryRequest request) {
        if (auditService != null) auditService.success(AuditActions.AUTH_PASSWORD_RECOVERY_REQUESTED, "AUTH", "User", null, null);
        String cui = User.normalizeCui(request.cui());
        Optional<User> userCandidate = userRepository.findByCuiForUpdate(cui);
        if (userCandidate.isEmpty() || userCandidate.get().getStatus() != UserStatus.ACTIVE) {
            consumeEquivalentHashingWork();
            return new PasswordRecoveryResponse(REQUEST_MESSAGE);
        }

        User user = userCandidate.get();
        Optional<Patient> patient = patientRepository.findByUser_Id(user.getId())
                .filter(candidate -> candidate.getEmail() != null && !candidate.getEmail().isBlank());
        if (patient.isEmpty()) {
            consumeEquivalentHashingWork();
            return new PasswordRecoveryResponse(REQUEST_MESSAGE);
        }

        Instant now = clock.instant();
        revokeOutstandingTokens(user.getId(), now);

        String code = generateCode();
        PasswordRecoveryToken token = new PasswordRecoveryToken(
                UUID.randomUUID(), user, passwordEncoder.encode(code), now, now.plus(properties.expiration()));
        tokenRepository.save(token);

        deliverAfterCommit(token.getId(), patient.get().getEmail(), code);
        return new PasswordRecoveryResponse(REQUEST_MESSAGE);
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public PasswordRecoveryResponse confirmRecovery(ConfirmPasswordRecoveryRequest request) {
        PasswordPolicy.validate(request.newPassword());
        String cui = User.normalizeCui(request.cui());
        User user = userRepository.findByCuiForUpdate(cui)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(this::invalidRecovery);
        if (patientRepository.findByUser_Id(user.getId()).isEmpty()) {
            throw invalidRecovery();
        }

        PasswordRecoveryToken token = tokenRepository
                .findFirstByUser_IdOrderByRequestedAtDesc(user.getId())
                .orElseThrow(this::invalidRecovery);
        Instant now = clock.instant();
        if (!token.isUsableAt(now)) {
            if (token.getUsedAt() == null && token.getRevokedAt() == null) {
                token.revoke(now);
                tokenRepository.save(token);
            }
            throw invalidRecovery();
        }

        if (!passwordEncoder.matches(request.code(), token.getCodeHash())) {
            token.recordFailedAttempt(properties.maxAttempts(), now);
            tokenRepository.save(token);
            throw invalidRecovery();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(now);
        userRepository.save(user);
        token.markUsed(now);
        tokenRepository.save(token);
        revokeOutstandingTokens(user.getId(), now);
        refreshTokenService.revokeAllForUser(user.getId());
        if (auditService != null) auditService.success(AuditActions.AUTH_PASSWORD_RECOVERY_COMPLETED, "AUTH", "User", user.getId(), user.getId());
        return new PasswordRecoveryResponse(SUCCESS_MESSAGE);
    }

    private void revokeOutstandingTokens(UUID userId, Instant now) {
        List<PasswordRecoveryToken> outstanding =
                tokenRepository.findAllByUser_IdAndUsedAtIsNullAndRevokedAtIsNull(userId);
        outstanding.forEach(token -> token.revoke(now));
        if (!outstanding.isEmpty()) {
            tokenRepository.saveAll(outstanding);
        }
    }

    private String generateCode() {
        return "%08d".formatted(secureRandom.nextInt(CODE_BOUND));
    }

    private void consumeEquivalentHashingWork() {
        passwordEncoder.encode(generateCode());
    }

    private void deliverAfterCommit(UUID requestId, String email, String code) {
        Runnable delivery = () -> {
            try {
                codeDelivery.deliver(email, code, properties.expiration());
            } catch (RuntimeException exception) {
                LOGGER.warn("Password recovery delivery could not be scheduled for request {}", requestId);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delivery.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delivery.run();
            }
        });
    }

    private BadRequestException invalidRecovery() {
        return new BadRequestException(INVALID_RECOVERY);
    }
}
