package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.request.PublicVerificationChannelRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentNotificationOutboxEvent;
import com.dentalcare.api.modules.appointments.model.AppointmentNotificationStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentNotificationOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AppointmentNotificationOutboxServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsAndDecryptsOutboxPayloadWithAuthenticatedEncryption() {
        AppointmentOutboxCrypto crypto = new AppointmentOutboxCrypto(properties());
        String encrypted = crypto.encrypt("temporary-code-123456");

        assertThat(encrypted).doesNotContain("temporary-code-123456");
        assertThat(crypto.decrypt(encrypted)).isEqualTo("temporary-code-123456");
        String tampered = (encrypted.startsWith("A") ? "B" : "A") + encrypted.substring(1);
        assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptedProviderDeliveryMarksSentAndErasesEncryptedOtp() {
        var fixture = fixture();
        UUID id = UUID.randomUUID();
        AppointmentNotificationOutboxEvent event = otpEvent(fixture, id, NOW.plusSeconds(600));
        event.claim(NOW);
        when(fixture.repository.findById(id)).thenReturn(Optional.of(event));

        fixture.service.dispatch(event);

        assertThat(event.getStatus()).isEqualTo(AppointmentNotificationStatus.SENT);
        assertThat(event.getRecipientCiphertext()).isNull();
        assertThat(event.getPayloadCiphertext()).isNull();
        verify(fixture.delivery).deliver(PublicVerificationChannelRequest.Channel.SMS, "+50255550101",
                "123456", Duration.ofSeconds(600));
    }

    @Test
    void providerFailureSchedulesRetryWithoutLosingEncryptedPayload() {
        var fixture = fixture();
        UUID id = UUID.randomUUID();
        AppointmentNotificationOutboxEvent event = otpEvent(fixture, id, NOW.plusSeconds(600));
        event.claim(NOW);
        when(fixture.repository.findById(id)).thenReturn(Optional.of(event));
        doThrow(new RuntimeException("provider response contains private data")).when(fixture.delivery)
                .deliver(any(), anyString(), anyString(), any());

        fixture.service.dispatch(event);

        assertThat(event.getStatus()).isEqualTo(AppointmentNotificationStatus.RETRY_PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastErrorCode()).isEqualTo("PROVIDER_DELIVERY_FAILED");
        assertThat(event.getPayloadCiphertext()).isNotBlank();
        assertThat(event.getRecipientCiphertext()).isNotBlank();
    }

    @Test
    void expiredOtpIsNeverSentAndSensitivePayloadIsErased() {
        var fixture = fixture();
        UUID id = UUID.randomUUID();
        AppointmentNotificationOutboxEvent event = otpEvent(fixture, id, NOW.minusSeconds(1));
        event.claim(NOW);
        when(fixture.repository.findById(id)).thenReturn(Optional.of(event));

        fixture.service.dispatch(event);

        assertThat(event.getStatus()).isEqualTo(AppointmentNotificationStatus.DEAD_LETTER);
        assertThat(event.getLastErrorCode()).isEqualTo("OTP_EXPIRED_BEFORE_DELIVERY");
        assertThat(event.getPayloadCiphertext()).isNull();
        verifyNoInteractions(fixture.delivery);
    }

    private AppointmentNotificationOutboxEvent otpEvent(Fixture fixture, UUID id, Instant expiry) {
        String destination = fixture.crypto.encrypt("+50255550101");
        String payload = fixture.crypto.encrypt("{\"kind\":\"OTP\",\"code\":\"123456\",\"validitySeconds\":600,\"message\":null,\"expiresAt\":\""
                + expiry + "\"}");
        return new AppointmentNotificationOutboxEvent(id, UUID.randomUUID(), "OTP", "SMS", destination,
                payload, id, id.toString(), NOW);
    }

    private Fixture fixture() {
        AppointmentNotificationOutboxRepository repository = mock(AppointmentNotificationOutboxRepository.class);
        PublicAppointmentCodeDelivery delivery = mock(PublicAppointmentCodeDelivery.class);
        AppointmentOutboxCrypto crypto = new AppointmentOutboxCrypto(properties());
        AppointmentNotificationOutboxService service = new AppointmentNotificationOutboxService(repository, crypto,
                properties(), delivery, new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(repository, delivery, crypto, service);
    }

    private AppointmentOutboxProperties properties() {
        return new AppointmentOutboxProperties(KEY, 3, Duration.ofSeconds(30), Duration.ofMinutes(2), 10);
    }

    private record Fixture(AppointmentNotificationOutboxRepository repository,
            PublicAppointmentCodeDelivery delivery, AppointmentOutboxCrypto crypto,
            AppointmentNotificationOutboxService service) {}
}
