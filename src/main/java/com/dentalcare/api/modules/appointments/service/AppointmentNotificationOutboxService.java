package com.dentalcare.api.modules.appointments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dentalcare.api.modules.appointments.dto.request.PublicVerificationChannelRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentNotificationOutboxResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentNotificationOutboxEvent;
import com.dentalcare.api.modules.appointments.repository.AppointmentNotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentNotificationOutboxService {
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(6);
    private final AppointmentNotificationOutboxRepository repository;
    private final AppointmentOutboxCrypto crypto;
    private final AppointmentOutboxProperties properties;
    private final PublicAppointmentCodeDelivery delivery;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AppointmentNotificationOutboxService(AppointmentNotificationOutboxRepository repository,
            AppointmentOutboxCrypto crypto, AppointmentOutboxProperties properties,
            PublicAppointmentCodeDelivery delivery, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.crypto = crypto;
        this.properties = properties;
        this.delivery = delivery;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public UUID enqueueOtp(UUID requestId, PublicVerificationChannelRequest.Channel channel,
            String destination, String code, Duration validity) {
        return enqueue(requestId, "OTP", channel.name(), destination,
                new DeliveryPayload("OTP", code, validity.toSeconds(), null, clock.instant().plus(validity)));
    }

    @Transactional
    public UUID enqueueNotice(UUID requestId, PublicVerificationChannelRequest.Channel channel,
            String destination, String text) {
        return enqueue(requestId, "NOTICE", channel.name(), destination,
                new DeliveryPayload("NOTICE", null, 0, text, null));
    }

    @Transactional
    public AppointmentNotificationOutboxEvent claimNext() {
        Instant now = clock.instant();
        return repository.claimNext(now, now.minus(properties.processingLease()))
                .map(event -> {
                    event.claim(now);
                    return event;
                }).orElse(null);
    }

    @Transactional
    public void markSent(UUID eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.markSent(clock.instant());
            repository.save(event);
        });
    }

    @Transactional
    public void markFailed(UUID eventId, String safeErrorCode) {
        repository.findById(eventId).ifPresent(event -> {
            boolean deadLetter = event.getAttempts() >= properties.maxAttempts();
            long multiplier = 1L << Math.min(Math.max(event.getAttempts() - 1, 0), 10);
            Duration retry = properties.baseRetryDelay().multipliedBy(multiplier);
            if (retry.compareTo(MAX_RETRY_DELAY) > 0) retry = MAX_RETRY_DELAY;
            event.markFailure(safeErrorCode, clock.instant().plus(retry), deadLetter);
            repository.save(event);
        });
    }

    @Transactional
    public void markExpired(UUID eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.markExpired("OTP_EXPIRED_BEFORE_DELIVERY");
            repository.save(event);
        });
    }

    @Transactional(readOnly = true)
    public List<AppointmentNotificationOutboxResponse> findForRequest(UUID requestId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return repository.findByAppointmentRequestIdOrderByCreatedAtDescIdDesc(requestId,
                        PageRequest.of(safePage, safeSize)).stream()
                .map(event -> new AppointmentNotificationOutboxResponse(event.getId(),
                        event.getAppointmentRequestId(), event.getEventType(), event.getChannel(),
                        event.getStatus(), event.getAttempts(), event.getCreatedAt(), event.getNextAttemptAt(),
                        event.getLastAttemptAt(), event.getSentAt(), event.getLastErrorCode(), event.getCorrelationId()))
                .toList();
    }

    public void dispatch(AppointmentNotificationOutboxEvent event) {
        DeliveryPayload payload;
        try {
            String destination = crypto.decrypt(event.getRecipientCiphertext());
            payload = objectMapper.readValue(crypto.decrypt(event.getPayloadCiphertext()), DeliveryPayload.class);
            var channel = PublicVerificationChannelRequest.Channel.valueOf(event.getChannel());
            if ("OTP".equals(payload.kind())) {
                if (payload.expiresAt() == null || !payload.expiresAt().isAfter(clock.instant())) {
                    markExpired(event.getId());
                    return;
                }
                delivery.deliver(channel, destination, payload.code(), Duration.ofSeconds(payload.validitySeconds()));
            } else {
                delivery.deliverNotice(channel, destination, payload.message());
            }
        } catch (Exception exception) {
            markFailed(event.getId(), exception instanceof IllegalStateException
                    && "OUTBOX_PAYLOAD_DECRYPTION_FAILED".equals(exception.getMessage())
                    ? "PAYLOAD_DECRYPTION_FAILED" : "PROVIDER_DELIVERY_FAILED");
            return;
        }
        markSent(event.getId());
    }

    private UUID enqueue(UUID requestId, String type, String channel, String recipient, DeliveryPayload payload) {
        try {
            Instant now = clock.instant();
            UUID eventId = UUID.randomUUID();
            repository.save(new AppointmentNotificationOutboxEvent(eventId, requestId, type, channel,
                    crypto.encrypt(recipient), crypto.encrypt(objectMapper.writeValueAsString(payload)),
                    eventId, eventId.toString(), now));
            return eventId;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize appointment notification payload", exception);
        }
    }

    public record DeliveryPayload(String kind, String code, long validitySeconds, String message, Instant expiresAt) {}
}
