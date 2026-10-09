package com.dentalcare.api.modules.appointments.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.dentalcare.api.security.jwt.JwtService;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PublicAppointmentConversationIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("INITIAL_ADMIN_ENABLED", () -> "false");
        registry.add("FRONTEND_URL", () -> "http://localhost:3000");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean JwtService jwtService;

    @Test
    void createsPublicRequestAndReadsConversationAndMessagesWithSameBearerToken() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "fullName", "Synthetic Chat Integration Visitor",
                "phone", "+502 5555-0198",
                "requestedAt", Instant.now().plusSeconds(86_400).toString(),
                "reason", "Primera consulta de prueba"));

        String receiptJson = mockMvc.perform(post("/api/v1/public/appointment-requests")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.conversationToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode receipt = objectMapper.readTree(receiptJson);
        String requestId = receipt.path("requestId").asText();
        String conversationToken = receipt.path("conversationToken").asText();
        org.junit.jupiter.api.Assertions.assertFalse(requestId.isBlank());
        org.junit.jupiter.api.Assertions.assertFalse(conversationToken.isBlank());
        String authorization = "Bearer " + conversationToken;

        mockMvc.perform(get("/api/v1/public/appointment-requests/{requestId}/conversation", requestId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.status").value("PENDING_CLINIC"));

        mockMvc.perform(get("/api/v1/public/appointment-requests/{requestId}/conversation/messages?size=20", requestId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.items[0].text").value(
                        "Recibimos tu solicitud. El horario solicitado es una preferencia y aún no está confirmado."));

        // Reading the conversation must not consume or rotate the bearer credential.
        mockMvc.perform(get("/api/v1/public/appointment-requests/{requestId}/conversation", requestId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk());

        String retryJson = mockMvc.perform(post("/api/v1/public/appointment-requests")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andReturn().getResponse().getContentAsString();
        String replacementToken = objectMapper.readTree(retryJson).path("conversationToken").asText();
        org.junit.jupiter.api.Assertions.assertNotEquals(conversationToken, replacementToken);

        mockMvc.perform(get("/api/v1/public/appointment-requests/{requestId}/conversation", requestId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId));
        mockMvc.perform(get("/api/v1/public/appointment-requests/{requestId}/conversation/messages?size=20", requestId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/appointment-requests/{requestId}/conversation", requestId)
                        .header("Authorization", "Bearer " + replacementToken))
                .andExpect(status().isOk());

        // Reading with raw token (without Bearer prefix) is accepted defensively
        mockMvc.perform(get("/api/v1/public/appointment-requests/{requestId}/conversation", requestId)
                        .header("Authorization", conversationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId));
    }
}
