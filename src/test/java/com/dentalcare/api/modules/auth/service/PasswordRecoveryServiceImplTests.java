package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.auth.config.PasswordRecoveryProperties;
import com.dentalcare.api.modules.auth.dto.request.ConfirmPasswordRecoveryRequest;
import com.dentalcare.api.modules.auth.dto.request.PasswordRecoveryRequest;
import com.dentalcare.api.modules.auth.model.PasswordRecoveryToken;
import com.dentalcare.api.modules.auth.repository.PasswordRecoveryTokenRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final String CUI = "1234567890123";

    @Mock private UserRepository userRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private PasswordRecoveryTokenRepository tokenRepository;
    @Mock private PasswordRecoveryCodeDelivery codeDelivery;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SecureRandom secureRandom;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private PasswordRecoveryServiceImpl service;
    private User user;
    private Patient patient;

    @BeforeEach
    void setUp() {
        user = new User(UUID.randomUUID(), "patient", "Patient Test", null, CUI,
                passwordEncoder.encode("OldPassword123!"), UserStatus.ACTIVE,
                NOW.minusSeconds(1000), NOW.minusSeconds(1000));
        patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);
        patient.setEmail("patient@example.com");

        service = new PasswordRecoveryServiceImpl(
                userRepository, patientRepository, tokenRepository, codeDelivery, refreshTokenService,
                passwordEncoder, new PasswordRecoveryProperties(Duration.ofMinutes(15), 5,
                "no-reply@dentalcare.test"), Clock.fixed(NOW, ZoneOffset.UTC), secureRandom);
    }

    @Test
    void requestCreatesHashedOneTimeCodeAndDeliversItWithoutReturningIt() {
        when(userRepository.findByCuiForUpdate(CUI)).thenReturn(Optional.of(user));
        when(patientRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(patient));
        when(tokenRepository.findAllByUser_IdAndUsedAtIsNullAndRevokedAtIsNull(user.getId()))
                .thenReturn(List.of());
        when(secureRandom.nextInt(100_000_000)).thenReturn(12_345_678);

        var response = service.requestRecovery(new PasswordRecoveryRequest(CUI));

        ArgumentCaptor<PasswordRecoveryToken> tokenCaptor = ArgumentCaptor.forClass(PasswordRecoveryToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordRecoveryToken persisted = tokenCaptor.getValue();
        assertThat(passwordEncoder.matches("12345678", persisted.getCodeHash())).isTrue();
        assertThat(persisted.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        verify(codeDelivery).deliver("patient@example.com", "12345678", Duration.ofMinutes(15));
        assertThat(response.message()).isEqualTo(PasswordRecoveryServiceImpl.REQUEST_MESSAGE);
        assertThat(response.toString()).doesNotContain("12345678");
    }

    @Test
    void requestForUnknownAccountReturnsSameGenericResponseWithoutCreatingToken() {
        when(userRepository.findByCuiForUpdate(CUI)).thenReturn(Optional.empty());

        var response = service.requestRecovery(new PasswordRecoveryRequest(CUI));

        assertThat(response.message()).isEqualTo(PasswordRecoveryServiceImpl.REQUEST_MESSAGE);
        verify(tokenRepository, never()).save(any());
        verify(codeDelivery, never()).deliver(any(), any(), any());
    }

    @Test
    void confirmChangesPasswordConsumesCodeAndRevokesEveryRefreshSession() {
        PasswordRecoveryToken token = token("87654321", NOW.plusSeconds(300));
        when(userRepository.findByCuiForUpdate(CUI)).thenReturn(Optional.of(user));
        when(patientRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(patient));
        when(tokenRepository.findFirstByUser_IdOrderByRequestedAtDesc(user.getId()))
                .thenReturn(Optional.of(token));
        when(tokenRepository.findAllByUser_IdAndUsedAtIsNullAndRevokedAtIsNull(user.getId()))
                .thenReturn(List.of());

        var response = service.confirmRecovery(
                new ConfirmPasswordRecoveryRequest(CUI, "87654321", "NewPassword456!"));

        assertThat(response.message()).isEqualTo(PasswordRecoveryServiceImpl.SUCCESS_MESSAGE);
        assertThat(passwordEncoder.matches("NewPassword456!", user.getPasswordHash())).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verify(refreshTokenService).revokeAllForUser(user.getId());
    }

    @Test
    void expiredCodeIsRevokedAndCannotResetPassword() {
        PasswordRecoveryToken token = token("87654321", NOW);
        stubConfirmation(token);

        assertThatThrownBy(() -> service.confirmRecovery(
                new ConfirmPasswordRecoveryRequest(CUI, "87654321", "NewPassword456!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PasswordRecoveryServiceImpl.INVALID_RECOVERY);

        assertThat(token.getRevokedAt()).isEqualTo(NOW);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void usedCodeCannotBeReused() {
        PasswordRecoveryToken token = token("87654321", NOW.plusSeconds(300));
        token.markUsed(NOW.minusSeconds(1));
        stubConfirmation(token);

        assertThatThrownBy(() -> service.confirmRecovery(
                new ConfirmPasswordRecoveryRequest(CUI, "87654321", "NewPassword456!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PasswordRecoveryServiceImpl.INVALID_RECOVERY);

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void invalidCodeRecordsAttemptAndRevokesAfterConfiguredMaximum() {
        PasswordRecoveryToken token = token("87654321", NOW.plusSeconds(300));
        for (int attempt = 0; attempt < 4; attempt++) {
            token.recordFailedAttempt(5, NOW.minusSeconds(1));
        }
        stubConfirmation(token);

        assertThatThrownBy(() -> service.confirmRecovery(
                new ConfirmPasswordRecoveryRequest(CUI, "00000000", "NewPassword456!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PasswordRecoveryServiceImpl.INVALID_RECOVERY);

        assertThat(token.getFailedAttempts()).isEqualTo(5);
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
        verify(userRepository, never()).save(any());
    }

    private PasswordRecoveryToken token(String code, Instant expiresAt) {
        return new PasswordRecoveryToken(UUID.randomUUID(), user, passwordEncoder.encode(code),
                NOW.minusSeconds(60), expiresAt);
    }

    private void stubConfirmation(PasswordRecoveryToken token) {
        when(userRepository.findByCuiForUpdate(CUI)).thenReturn(Optional.of(user));
        when(patientRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(patient));
        when(tokenRepository.findFirstByUser_IdOrderByRequestedAtDesc(user.getId()))
                .thenReturn(Optional.of(token));
    }
}
