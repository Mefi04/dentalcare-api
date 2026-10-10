package com.dentalcare.api.modules.assistant.service;

import com.dentalcare.api.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GeminiHttpGateway implements GeminiGateway {
    private final RestClient client;
    private final String apiKey;
    private final String model;

    public GeminiHttpGateway(@Value("${dentalcare.assistant.gemini-api-key:}") String apiKey,
            @Value("${dentalcare.assistant.model:gemini-3.8-flash}") String model) {
        this.apiKey = apiKey;
        if (model == null || !model.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid Gemini model identifier");
        }
        this.model = model;
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requestFactory).build();
    }

    @Override
    public String generate(String instruction, String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceUnavailableException("Public assistant is not configured");
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("maxOutputTokens", 2048, "temperature", 0.3));
        try {
            JsonNode response = client.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .body(body)
                    .retrieve().body(JsonNode.class);
            JsonNode parts = response == null ? null
                    : response.path("candidates").path(0).path("content").path("parts");
            if (parts == null || !parts.isArray()) {
                throw new ServiceUnavailableException("Public assistant could not answer now");
            }
            StringBuilder answer = new StringBuilder();
            parts.forEach(part -> {
                if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) {
                    answer.append(part.path("text").asText());
                }
            });
            if (answer.isEmpty()) {
                throw new ServiceUnavailableException("Public assistant could not answer now");
            }
            return answer.toString().trim();
        } catch (RestClientException exception) {
            throw new ServiceUnavailableException("Public assistant is temporarily unavailable");
        }
    }
}
