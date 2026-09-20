package com.dentalcare.api.exception;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTests {

    private static final String INTERNAL_EXCEPTION_MESSAGE =
            "database password=super-secret failed at InternalService.java:42";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsBadRequestResponse() throws Exception {
        mockMvc.perform(get("/test/errors/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.path").value("/test/errors/bad-request"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void returnsNotFoundResponse() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void returnsConflictResponse() throws Exception {
        mockMvc.perform(get("/test/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Resource conflict"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void returnsFieldErrorsForInvalidRequest() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors", hasKey("name")))
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));
    }

    @Test
    void returnsSafeInternalServerErrorResponse() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/test/errors/unexpected"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void internalServerErrorDoesNotExposeOriginalExceptionMessage() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(not(INTERNAL_EXCEPTION_MESSAGE)))
                .andExpect(jsonPath("$", not(hasKey("exception"))))
                .andExpect(jsonPath("$", not(hasKey("trace"))));
    }

    @RestController
    @RequestMapping("/test/errors")
    static class TestController {

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException("Invalid request");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Resource not found");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Resource conflict");
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {
            // Validation occurs before the controller body is invoked.
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException(INTERNAL_EXCEPTION_MESSAGE);
        }
    }

    record TestRequest(@NotBlank(message = "Name is required") String name) {
    }
}
