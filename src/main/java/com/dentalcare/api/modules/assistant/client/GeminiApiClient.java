package com.dentalcare.api.modules.assistant.client;

import com.dentalcare.api.modules.assistant.config.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

@Component
public class GeminiApiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiApiClient.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}";

    public static final String TOOL_GET_SLOTS = "get_available_general_appointment_slots";

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public record FunctionCall(String name, Map<String, Object> arguments) {}
    public record GeminiResponse(String text, FunctionCall functionCall) {}

    public GeminiApiClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = (int) properties.getRequestTimeout().toMillis();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isConfigured() {
        return properties.isEnabled() && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    public Optional<GeminiResponse> generateContent(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();

            // systemInstruction
            ObjectNode systemInstruction = root.putObject("systemInstruction");
            ArrayNode sysParts = systemInstruction.putArray("parts");
            sysParts.addObject().put("text", systemPrompt);

            // contents
            ArrayNode contents = root.putArray("contents");
            ObjectNode userContent = contents.addObject();
            userContent.put("role", "user");
            ArrayNode userParts = userContent.putArray("parts");
            userParts.addObject().put("text", userMessage);

            // tools definition
            ArrayNode tools = root.putArray("tools");
            ObjectNode functionDeclarationsWrapper = tools.addObject();
            ArrayNode funcDecls = functionDeclarationsWrapper.putArray("functionDeclarations");
            ObjectNode toolDecl = funcDecls.addObject();
            toolDecl.put("name", TOOL_GET_SLOTS);
            toolDecl.put("description", "Consulta los horarios de citas disponibles para odontología general en una fecha específica (formato YYYY-MM-DD)");
            ObjectNode parameters = toolDecl.putObject("parameters");
            parameters.put("type", "OBJECT");
            ObjectNode props = parameters.putObject("properties");
            ObjectNode dateProp = props.putObject("date");
            dateProp.put("type", "STRING");
            dateProp.put("description", "Fecha a consultar en formato YYYY-MM-DD");
            ArrayNode reqProps = parameters.putArray("required");
            reqProps.add("date");

            // generationConfig
            ObjectNode genConfig = root.putObject("generationConfig");
            genConfig.put("maxOutputTokens", properties.getMaxOutputTokens());
            genConfig.put("temperature", 0.2);

            String requestBody = objectMapper.writeValueAsString(root);

            String responseBody = restClient.post()
                    .uri(GEMINI_API_URL, properties.getModel(), properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            return parseResponse(responseBody);
        } catch (Exception e) {
            log.warn("Gemini API call failed or timed out: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> sendToolResponse(String systemPrompt, String userMessage,
                                             String functionName, String toolResultJson) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();

            // systemInstruction
            ObjectNode systemInstruction = root.putObject("systemInstruction");
            systemInstruction.putArray("parts").addObject().put("text", systemPrompt);

            // contents
            ArrayNode contents = root.putArray("contents");

            // Turn 1: user query
            ObjectNode userTurn = contents.addObject();
            userTurn.put("role", "user");
            userTurn.putArray("parts").addObject().put("text", userMessage);

            // Turn 2: model function call
            ObjectNode modelTurn = contents.addObject();
            modelTurn.put("role", "model");
            ObjectNode funcCallPart = modelTurn.putArray("parts").addObject();
            ObjectNode funcCallNode = funcCallPart.putObject("functionCall");
            funcCallNode.put("name", functionName);
            funcCallNode.putObject("args");

            // Turn 3: function response
            ObjectNode funcResponseTurn = contents.addObject();
            funcResponseTurn.put("role", "user");
            ObjectNode funcResponsePart = funcResponseTurn.putArray("parts").addObject();
            ObjectNode funcRespNode = funcResponsePart.putObject("functionResponse");
            funcRespNode.put("name", functionName);
            JsonNode resultNode = objectMapper.readTree(toolResultJson);
            funcRespNode.set("response", resultNode);

            // generationConfig
            ObjectNode genConfig = root.putObject("generationConfig");
            genConfig.put("maxOutputTokens", properties.getMaxOutputTokens());
            genConfig.put("temperature", 0.2);

            String responseBody = restClient.post()
                    .uri(GEMINI_API_URL, properties.getModel(), properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(root))
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            JsonNode tree = objectMapper.readTree(responseBody);
            JsonNode candidate = tree.path("candidates").path(0);
            JsonNode part = candidate.path("content").path("parts").path(0);
            if (part.has("text")) {
                return Optional.of(part.get("text").asText());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("Gemini API tool response dispatch failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<GeminiResponse> parseResponse(String json) {
        try {
            JsonNode tree = objectMapper.readTree(json);
            JsonNode candidate = tree.path("candidates").path(0);
            JsonNode parts = candidate.path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                JsonNode firstPart = parts.get(0);
                if (firstPart.has("functionCall")) {
                    JsonNode fc = firstPart.get("functionCall");
                    String name = fc.path("name").asText();
                    Map<String, Object> args = objectMapper.convertValue(fc.path("args"), Map.class);
                    return Optional.of(new GeminiResponse(null, new FunctionCall(name, args)));
                } else if (firstPart.has("text")) {
                    return Optional.of(new GeminiResponse(firstPart.get("text").asText(), null));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
