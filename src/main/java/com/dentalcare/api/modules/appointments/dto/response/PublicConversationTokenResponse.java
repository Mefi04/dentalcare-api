package com.dentalcare.api.modules.appointments.dto.response;
import java.time.Instant;
public record PublicConversationTokenResponse(String conversationToken, String tokenType, Instant expiresAt) {}
