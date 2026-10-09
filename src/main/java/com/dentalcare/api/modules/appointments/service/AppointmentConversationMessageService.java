package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.exception.GoneException;
import com.dentalcare.api.modules.appointments.dto.request.CreateAppointmentConversationMessageRequest;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentConversationMessagesPageResponse;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestMessageResponse;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestMessage;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentPublicConversationRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestMessageRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.appointments.dto.request.PublicVerificationChannelRequest;
import com.dentalcare.api.modules.users.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentConversationMessageService {
    private static final String NOT_PUBLIC = "APPOINTMENT_REQUEST_NOT_PUBLIC";
    private static final String STATE_NOT_ELIGIBLE = "APPOINTMENT_REQUEST_STATE_NOT_ELIGIBLE";
    private final AppointmentRequestRepository requests;
    private final AppointmentRequestMessageRepository messages;
    private final AppointmentPublicConversationRepository conversations;
    private final UserRepository users;
    private final AppointmentNotificationOutboxService notificationOutbox;
    private final PublicAppointmentCodeDelivery delivery;
    private final Clock clock;

    public AppointmentConversationMessageService(AppointmentRequestRepository requests,
            AppointmentRequestMessageRepository messages, AppointmentPublicConversationRepository conversations,
            UserRepository users, AppointmentNotificationOutboxService notificationOutbox,
            PublicAppointmentCodeDelivery delivery, Clock clock) {
        this.requests = requests;
        this.messages = messages;
        this.conversations = conversations;
        this.users = users;
        this.notificationOutbox = notificationOutbox;
        this.delivery = delivery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AppointmentConversationMessagesPageResponse getPublic(UUID requestId, String token, String cursor, int size) {
        authorize(requestId, token);
        return page(requestId, cursor, size);
    }

    @Transactional
    public AppointmentRequestMessageResponse addPublic(UUID requestId, String token, UUID key,
            CreateAppointmentConversationMessageRequest input) {
        authorize(requestId, token);
        return save(requestId, "PATIENT", key, input.text());
    }

    @Transactional(readOnly = true)
    public AppointmentConversationMessagesPageResponse getAdministrative(UUID requestId, String cursor, int size) {
        AppointmentRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
        requirePublic(request);
        return page(requestId, cursor, size);
    }

    @Transactional
    public AppointmentRequestMessageResponse addAdministrative(UUID actorId, UUID requestId, UUID key,
            CreateAppointmentConversationMessageRequest input) {
        if (actorId == null || users.findById(actorId).isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }
        return save(requestId, "RECEPTION", key, input.text());
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestMessage> latest(UUID requestId, int limit) {
        List<AppointmentRequestMessage> result = messages.findLatest(requestId, PageRequest.of(0, limit));
        java.util.Collections.reverse(result);
        return result;
    }

    private AppointmentRequestMessageResponse save(UUID requestId, String sender, UUID key, String rawText) {
        if (key == null) throw new BadRequestException("Idempotency-Key UUID is required");
        if (rawText == null || rawText.isBlank() || rawText.length() > 500
                || rawText.indexOf('<') >= 0 || rawText.indexOf('>') >= 0
                || rawText.chars().anyMatch(Character::isISOControl)) {
            throw new BadRequestException("Message must be plain text between 1 and 500 characters");
        }
        String text = rawText.trim();
        NonClinicalSchedulingText.validate(text);
        AppointmentRequest request = requests.findDetailedByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request not found"));
        requirePublic(request);
        if (!isOpen(request)) throw new ConflictException(STATE_NOT_ELIGIBLE, "Messages require an open public request");
        String hash = sha256(text);
        var previous = messages.findByAppointmentRequestIdAndSenderAndIdempotencyKey(requestId, sender, key);
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getIdempotencyPayloadHash())) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was used with different message content");
            }
            return response(previous.get());
        }
        AppointmentRequestMessage saved = messages.saveAndFlush(new AppointmentRequestMessage(UUID.randomUUID(),
                requestId, sender, "FREE_TEXT", text, clock.instant(), key, hash));
        if ("RECEPTION".equals(sender)) enqueueNotice(request, "Recepción te envió un mensaje. Ingresa al portal para consultarlo.");
        return response(saved);
    }

    private AppointmentConversationMessagesPageResponse page(UUID requestId, String cursor, int size) {
        if (size < 1 || size > 100) throw new BadRequestException("size must be between 1 and 100");
        List<AppointmentRequestMessage> found;
        if (cursor == null || cursor.isBlank()) {
            found = messages.findLatest(requestId, PageRequest.of(0, size + 1));
        } else {
            String decoded;
            try {
                decoded = new String(java.util.Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Invalid message cursor");
            }
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) throw new BadRequestException("Invalid message cursor");
            try {
                found = messages.findOlderThan(requestId, Instant.parse(parts[0]), UUID.fromString(parts[1]),
                        PageRequest.of(0, size + 1));
            } catch (RuntimeException exception) {
                throw new BadRequestException("Invalid message cursor");
            }
        }
        boolean more = found.size() > size;
        if (more) found = new java.util.ArrayList<>(found.subList(0, size));
        String next = null;
        if (more && !found.isEmpty()) {
            AppointmentRequestMessage last = found.get(found.size() - 1);
            next = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (last.getCreatedAt() + "|" + last.getId()).getBytes(StandardCharsets.UTF_8));
        }
        java.util.Collections.reverse(found);
        return new AppointmentConversationMessagesPageResponse(found.stream().map(this::response).toList(), next, more, size);
    }

    private void authorize(UUID requestId, String token) {
        if (token == null || token.isBlank()) throw new UnauthorizedException("Conversation token is required");
        var conversation = conversations.findByConversationTokenHash(sha256(token))
                .filter(value -> requestId.equals(value.getAppointmentRequestId()))
                .orElseThrow(() -> new UnauthorizedException("Invalid conversation token"));
        if (conversation.getConversationExpiresAt() == null || !conversation.getConversationExpiresAt().isAfter(clock.instant())) {
            throw new GoneException("Conversation token has expired; verify your contact again");
        }
    }

    private void requirePublic(AppointmentRequest request) {
        if (request.getRequesterFullName() == null) {
            throw new ConflictException(NOT_PUBLIC, "Conversation history is available only for public requests");
        }
    }

    private boolean isOpen(AppointmentRequest request) {
        return request.getStatus() == AppointmentRequestStatus.PENDING_CLINIC
                || request.getStatus() == AppointmentRequestStatus.PENDING_PATIENT;
    }

    private void enqueueNotice(AppointmentRequest request, String text) {
        if (request.getRequesterEmail() != null && !request.getRequesterEmail().isBlank()
                && delivery.isConfigured(PublicVerificationChannelRequest.Channel.EMAIL)) {
            notificationOutbox.enqueueNotice(request.getId(), PublicVerificationChannelRequest.Channel.EMAIL,
                    request.getRequesterEmail(), text);
        } else if (request.getRequesterPhone() != null && !request.getRequesterPhone().isBlank()
                && delivery.isConfigured(PublicVerificationChannelRequest.Channel.SMS)) {
            notificationOutbox.enqueueNotice(request.getId(), PublicVerificationChannelRequest.Channel.SMS,
                    request.getRequesterPhone(), text);
        }
    }

    private AppointmentRequestMessageResponse response(AppointmentRequestMessage message) {
        return new AppointmentRequestMessageResponse(message.getId(), message.getSender(), message.getMessageType(),
                message.getText(), message.getCreatedAt());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
