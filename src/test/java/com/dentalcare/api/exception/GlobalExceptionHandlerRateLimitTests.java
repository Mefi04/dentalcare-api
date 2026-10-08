package com.dentalcare.api.exception;

import com.dentalcare.api.shared.response.ApiErrorResponse;
import com.dentalcare.api.security.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerRateLimitTests {

    @Test
    void returns429AndRetryAfterWhenRateLimitExceeded() {
        // Arrange
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RateLimitExceededException ex = new RateLimitExceededException(60);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/reports/dashboard");

        // Act
        ResponseEntity<ApiErrorResponse> response = handler.handleRateLimit(ex, request);

        // Assert
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("60", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getBody().status());
    }
}
