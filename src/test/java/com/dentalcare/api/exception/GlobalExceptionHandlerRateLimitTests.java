package com.dentalcare.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalcare.api.security.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerRateLimitTests {
    @Test
    void returnsConsistent429WithRetryAfterAndGenericBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRequestURI("/api/v1/auth/login");
        var response = new GlobalExceptionHandler().handleRateLimit(new RateLimitExceededException(37), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("37");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Too many requests. Please try again later");
        assertThat(response.getBody().message()).doesNotContain("user", "CUI", "account");
    }
}
