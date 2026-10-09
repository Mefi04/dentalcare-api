package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentConversationMessageRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentPublicConversation;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestMessage;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentPublicConversationRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestMessageRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AppointmentConversationMessageServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private final AppointmentRequestRepository requests = mock(AppointmentRequestRepository.class);
    private final AppointmentRequestMessageRepository messages = mock(AppointmentRequestMessageRepository.class);
    private final AppointmentPublicConversationRepository conversations = mock(AppointmentPublicConversationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AppointmentNotificationOutboxService outbox = mock(AppointmentNotificationOutboxService.class);
    private final PublicAppointmentCodeDelivery delivery = mock(PublicAppointmentCodeDelivery.class);
    private final AppointmentConversationMessageService service = new AppointmentConversationMessageService(
            requests, messages, conversations, users, outbox, delivery, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void scopedTokenCannotReadAnotherRequestConversation() {
        UUID expectedRequestId = UUID.randomUUID();
        String token = "private-token";
        when(conversations.findByConversationTokenHash(hash(token)))
                .thenReturn(Optional.of(verifiedConversation(UUID.randomUUID(), token)));

        assertThatThrownBy(() -> service.getPublic(expectedRequestId, token, null, 20))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(messages);
    }

    @Test
    void sendsNonClinicalMessageWithServerDerivedSenderAndIdempotency() {
        UUID requestId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        String token = "scoped-token";
        AppointmentRequest request = publicRequest(requestId);
        when(conversations.findByConversationTokenHash(hash(token)))
                .thenReturn(Optional.of(verifiedConversation(requestId, token)));
        when(requests.findDetailedByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(messages.findByAppointmentRequestIdAndSenderAndIdempotencyKey(requestId, "PATIENT", key))
                .thenReturn(Optional.empty());
        when(messages.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.addPublic(requestId, token, key,
                new CreateAppointmentConversationMessageRequest("Please call after 3 pm"));

        assertThat(result.sender()).isEqualTo("PATIENT");
        assertThat(result.type()).isEqualTo("FREE_TEXT");
        assertThat(result.text()).isEqualTo("Please call after 3 pm");
        verify(messages).saveAndFlush(argThat(message -> message.getSender().equals("PATIENT")
                && message.getIdempotencyKey().equals(key)));
    }

    @Test
    void idempotencyKeyCannotBeReusedWithDifferentText() {
        UUID requestId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        String token = "scoped-token";
        AppointmentRequest request = publicRequest(requestId);
        AppointmentRequestMessage old = new AppointmentRequestMessage(UUID.randomUUID(), requestId, "PATIENT",
                "FREE_TEXT", "Please call after 3 pm", NOW, key, hash("Please call after 3 pm"));
        when(conversations.findByConversationTokenHash(hash(token)))
                .thenReturn(Optional.of(verifiedConversation(requestId, token)));
        when(requests.findDetailedByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(messages.findByAppointmentRequestIdAndSenderAndIdempotencyKey(requestId, "PATIENT", key))
                .thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.addPublic(requestId, token, key,
                new CreateAppointmentConversationMessageRequest("Call me tomorrow")))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_REUSED");
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void cursorPageIsBoundedChronologicalAndReturnsStableOlderCursor() {
        UUID requestId = UUID.randomUUID();
        String token = "scoped-token";
        AppointmentRequestMessage latest = message(requestId, NOW.plusSeconds(2), "latest");
        AppointmentRequestMessage older = message(requestId, NOW.plusSeconds(1), "older");
        when(conversations.findByConversationTokenHash(hash(token)))
                .thenReturn(Optional.of(verifiedConversation(requestId, token)));
        when(messages.findLatest(eq(requestId), any(Pageable.class))).thenReturn(List.of(latest, older));

        var page = service.getPublic(requestId, token, null, 1);

        assertThat(page.items()).extracting(item -> item.text()).containsExactly("latest");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.pageSize()).isEqualTo(1);
        String expected = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                (latest.getCreatedAt() + "|" + latest.getId()).getBytes(StandardCharsets.UTF_8));
        assertThat(page.nextCursor()).isEqualTo(expected);
    }

    private AppointmentRequest publicRequest(UUID id) {
        return new AppointmentRequest(id, null, null, NOW.plusSeconds(86400),
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Requester", null,
                "+50255550101", null, null, UUID.randomUUID(), "a".repeat(64));
    }

    private AppointmentPublicConversation verifiedConversation(UUID requestId, String token) {
        var value = new AppointmentPublicConversation(requestId, "SMS", null, null, NOW);
        value.consumeCode(hash(token), NOW.plusSeconds(3600), NOW);
        return value;
    }

    private AppointmentRequestMessage message(UUID requestId, Instant createdAt, String text) {
        return new AppointmentRequestMessage(UUID.randomUUID(), requestId, "BOT", "SCHEDULING_UPDATE", text, createdAt);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
