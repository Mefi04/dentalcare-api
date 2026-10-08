package com.dentalcare.api.config;

import com.dentalcare.api.security.cookie.AuthCookieManager;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";
    static final String REFRESH_COOKIE = "refreshCookie";
    private static final String ERROR_SCHEMA = "#/components/schemas/ApiErrorResponse";
    private static final Set<String> WEB_REFRESH_COOKIE_PATHS = Set.of(
            "/api/v1/auth/refresh", "/api/v1/auth/logout");
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/activate", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/mobile/login", "/api/v1/auth/mobile/refresh", "/api/v1/auth/mobile/logout",
            "/api/v1/auth/password-recovery/request", "/api/v1/auth/password-recovery/confirm");
    private static final Set<String> AUTHENTICATION_FAILURE_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/mobile/login", "/api/v1/auth/mobile/refresh");
    private static final Set<String> PATIENT_AUTH_PATHS = Set.of("/api/v1/auth/mobile/password");

    @Bean
    OpenAPI dentalCareOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token issued by the web or mobile authentication flow."))
                .addSecuritySchemes(REFRESH_COOKIE, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
                        .name(AuthCookieManager.REFRESH_TOKEN_COOKIE_NAME)
                        .description("HttpOnly refresh-session cookie used only by web refresh and logout."));
        return new OpenAPI()
                .info(new Info().title("DentalCare API").version("1.0")
                        .description("Versioned REST contract consumed by DentalCare Web and Mobile clients."))
                .components(components)
                .tags(List.of(
                        new Tag().name("Audience: Public").description("No access token required."),
                        new Tag().name("Audience: Staff")
                                .description("Requires staff permissions documented by each operation."),
                        new Tag().name("Audience: Patient self-service")
                                .description("Requires ROLE_PATIENT and derives ownership exclusively from the JWT."),
                        new Tag().name("Audience: Authenticated")
                                .description("Available to any authenticated account type.")));
    }

    @Bean
    OpenApiCustomizer dentalCareContractCustomizer() {
        return openApi -> {
            openApi.setServers(List.of());
            openApi.getComponents().addSchemas("ApiErrorResponse", errorSchema());
            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((method, operation) -> {
                        classifyAndSecure(path, operation);
                        documentCommonResponses(path, method.name(), operation);
                    }));
        };
    }

    private io.swagger.v3.oas.models.media.Schema<?> errorSchema() {
        return new ObjectSchema()
                .addProperty("timestamp", new DateTimeSchema())
                .addProperty("status", new IntegerSchema().format("int32"))
                .addProperty("error", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("path", new StringSchema())
                .addProperty("fieldErrors", new ObjectSchema()
                        .description("Validation errors keyed by request field; values are human-readable strings."))
                .required(List.of("timestamp", "status", "error", "message", "path", "fieldErrors"));
    }

    private void classifyAndSecure(String path, Operation operation) {
        if (path.startsWith("/api/v1/patients/me") || PATIENT_AUTH_PATHS.contains(path)) {
            operation.addTagsItem("Audience: Patient self-service");
            operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
        } else if (path.startsWith("/api/v1/public/") || PUBLIC_AUTH_PATHS.contains(path)) {
            operation.addTagsItem("Audience: Public");
            operation.setSecurity(WEB_REFRESH_COOKIE_PATHS.contains(path)
                    ? List.of(new SecurityRequirement().addList(REFRESH_COOKIE)) : List.of());
        } else if ("/api/v1/auth/me".equals(path)) {
            operation.addTagsItem("Audience: Authenticated");
            operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
        } else if (path.startsWith("/api/v1/")) {
            operation.addTagsItem("Audience: Staff");
            operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
        }
    }

    private void documentCommonResponses(String path, String method, Operation operation) {
        ApiResponses responses = operation.getResponses();
        addError(responses, "400", "Invalid request, failed validation, or page offset > 1000");
        if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
            addError(responses, "401", "Authentication is required or invalid");
            addError(responses, "403", "Authenticated caller lacks the required permission");
        }
        if (AUTHENTICATION_FAILURE_PATHS.contains(path)) {
            addError(responses, "401", "Credentials or refresh session are invalid");
        }
        if (path.contains("{")) {
            addError(responses, "404", "Resource was not found or is not visible to this caller");
        }
        if (!"GET".equals(method)) {
            addError(responses, "409", "Operation conflicts with the current resource state");
        }
        
        // Rate limiting is globally applied
        ApiResponse tooManyRequests = errorResponse("Request rate limit exceeded");
        tooManyRequests.addHeaderObject("Retry-After", new io.swagger.v3.oas.models.headers.Header()
                .description("Seconds until the request may be retried")
                .schema(new IntegerSchema().format("int64")));
        responses.addApiResponse("429", tooManyRequests);

        if (path.startsWith("/api/v1/reports")) {
            ApiResponse serviceUnavailable = errorResponse("Service is saturated, please try again later");
            serviceUnavailable.addHeaderObject("Retry-After", new io.swagger.v3.oas.models.headers.Header()
                .description("Seconds until the request may be retried")
                .schema(new IntegerSchema().format("int64")));
            responses.addApiResponse("503", serviceUnavailable);
        }
    }

    private void addError(ApiResponses responses, String status, String description) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(status, errorResponse(description));
        }
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().schema(new io.swagger.v3.oas.models.media.Schema<>().$ref(ERROR_SCHEMA))));
    }
}
