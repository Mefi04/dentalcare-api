package com.dentalcare.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.dentalcare.api.security.cookie.AuthCookieManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "dentalcare.cors.allowed-origin=http://localhost:3000",
        "dentalcare.openapi.public-access=true"
})
@AutoConfigureMockMvc
class OpenApiContractIntegrationTests extends DentalCareApplicationTests {

    private static final Path COMMITTED_SPEC = Path.of("docs", "openapi", "dentalcare-api.json");
    private static final Path GENERATED_SPEC = Path.of("target", "openapi", "dentalcare-api.json");
    private static final Set<String> PRIORITY_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/mobile/login", "/api/v1/patients/me/profile",
            "/api/v1/patients/me/appointments", "/api/v1/patients/me/treatment-plans",
            "/api/v1/patients/me/treatment-budgets", "/api/v1/patients/me/documents",
            "/api/v1/patients/me/prescriptions", "/api/v1/patients/me/account-statement",
            "/api/v1/public/clinic", "/api/v1/public/services");
    private static final Set<String> FORBIDDEN_ENTITY_SCHEMAS = Set.of(
            "User", "Role", "Permission", "Patient", "Appointment", "TreatmentPlan",
            "TreatmentBudget", "ClinicalDocument", "Prescription", "Charge", "Payment");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void publishedContractIsValidReproducibleAndSafeForClients() throws Exception {
        String source = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(source);

        assertThat(root.path("openapi").asText()).startsWith("3.");
        assertThat(root.path("info").path("title").asText()).isEqualTo("DentalCare API");
        PRIORITY_PATHS.forEach(path -> assertThat(root.path("paths").has(path))
                .as("priority path %s", path).isTrue());
        assertSecuritySchemes(root);
        assertPrincipalResponsesAreDocumented(root);
        assertReferencesResolve(root, root);
        assertPatientSelfServiceHasNoOwnerSelector(root);
        assertNoJpaEntitiesArePublished(root);

        String canonical = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(canonicalize(root)) + System.lineSeparator();
        Files.createDirectories(GENERATED_SPEC.getParent());
        Files.writeString(GENERATED_SPEC, canonical, StandardCharsets.UTF_8);

        if (Boolean.getBoolean("openapi.snapshot.update")) {
            Files.createDirectories(COMMITTED_SPEC.getParent());
            Files.writeString(COMMITTED_SPEC, canonical, StandardCharsets.UTF_8);
        }
        assertThat(COMMITTED_SPEC).as("committed OpenAPI snapshot").exists();
        assertThat(Files.readString(COMMITTED_SPEC, StandardCharsets.UTF_8)).isEqualTo(canonical);
    }

    private void assertSecuritySchemes(JsonNode root) {
        JsonNode schemes = root.path("components").path("securitySchemes");
        assertThat(schemes.path("bearerAuth").path("type").asText()).isEqualTo("http");
        assertThat(schemes.path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(schemes.path("refreshCookie").path("in").asText()).isEqualTo("cookie");
        assertThat(schemes.path("refreshCookie").path("name").asText())
                .isEqualTo(AuthCookieManager.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(root.at("/paths/~1api~1v1~1auth~1refresh/post/security/0/refreshCookie").isArray()).isTrue();
        assertThat(root.at("/paths/~1api~1v1~1patients~1me~1profile/get/security/0/bearerAuth").isArray()).isTrue();
        assertThat(root.at("/paths/~1api~1v1~1public~1clinic/get/security").isArray()).isTrue();
        assertThat(root.at("/paths/~1api~1v1~1public~1clinic/get/security").isEmpty()).isTrue();
    }

    private void assertPatientSelfServiceHasNoOwnerSelector(JsonNode root) {
        root.path("paths").properties().forEach(pathEntry -> {
            if (!pathEntry.getKey().startsWith("/api/v1/patients/me")) {
                return;
            }
            pathEntry.getValue().properties().forEach(operationEntry -> {
                JsonNode parameters = operationEntry.getValue().path("parameters");
                if (parameters.isArray()) {
                    parameters.forEach(parameter -> assertThat(parameter.path("name").asText())
                            .isNotIn("patientId", "userId"));
                }
            });
        });
    }

    private void assertPrincipalResponsesAreDocumented(JsonNode root) {
        JsonNode patientProfile = root.at("/paths/~1api~1v1~1patients~1me~1profile/get/responses");
        assertThat(patientProfile.has("200")).isTrue();
        assertThat(patientProfile.has("400")).isTrue();
        assertThat(patientProfile.has("401")).isTrue();
        assertThat(patientProfile.has("403")).isTrue();

        JsonNode login = root.at("/paths/~1api~1v1~1auth~1login/post/responses");
        assertThat(login.has("200")).isTrue();
        assertThat(login.has("400")).isTrue();
        assertThat(login.has("401")).isTrue();
        assertThat(login.has("429")).isTrue();
        assertThat(login.at("/429/headers/Retry-After").isObject()).isTrue();
        assertThat(root.at("/paths/~1api~1v1~1patients~1me~1appointments~1{appointmentId}~1cancel/patch/responses/409")
                .isObject()).isTrue();
        assertThat(root.at("/components/schemas/ApiErrorResponse/properties/fieldErrors").isObject()).isTrue();
    }

    private void assertReferencesResolve(JsonNode root, JsonNode node) {
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null && reference.isTextual() && reference.asText().startsWith("#/")) {
                assertThat(root.at(reference.asText().substring(1)).isMissingNode())
                        .as("OpenAPI reference %s", reference.asText()).isFalse();
            }
            node.properties().forEach(entry -> assertReferencesResolve(root, entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(value -> assertReferencesResolve(root, value));
        }
    }

    private void assertNoJpaEntitiesArePublished(JsonNode root) {
        Set<String> schemas = new java.util.HashSet<>();
        root.path("components").path("schemas").fieldNames().forEachRemaining(schemas::add);
        assertThat(schemas).doesNotContainAnyElementsOf(FORBIDDEN_ENTITY_SCHEMAS);
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), canonicalize(entry.getValue())));
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(value -> array.add(canonicalize(value)));
            return array;
        }
        return node;
    }
}
