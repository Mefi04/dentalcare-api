package com.dentalcare.api.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ClinicalDocumentConcurrencyInterceptorTests {

    @Test
    void rejectsConcurrentUploadAndReleasesPermitAfterCompletion() {
        ClinicalDocumentConcurrencyInterceptor interceptor =
                new ClinicalDocumentConcurrencyInterceptor(1, 1);
        MockHttpServletRequest first = request("POST", "/api/v1/patients/p1/documents/upload");
        MockHttpServletRequest second = request("POST", "/api/v1/patients/p2/documents/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> interceptor.preHandle(first, response, new Object())).doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preHandle(second, response, new Object()))
                .isInstanceOf(RateLimitExceededException.class);

        interceptor.afterCompletion(first, response, new Object(), null);
        assertThatCode(() -> interceptor.preHandle(second, response, new Object())).doesNotThrowAnyException();
        interceptor.afterCompletion(second, response, new Object(), null);
    }

    @Test
    void uploadAndDownloadUseIndependentPermitPools() {
        ClinicalDocumentConcurrencyInterceptor interceptor =
                new ClinicalDocumentConcurrencyInterceptor(1, 1);
        MockHttpServletRequest upload = request("POST", "/api/v1/patients/p1/documents/upload");
        MockHttpServletRequest download = request("GET", "/api/v1/patients/me/documents/d1/download");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> interceptor.preHandle(upload, response, new Object())).doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preHandle(download, response, new Object())).doesNotThrowAnyException();
        interceptor.afterCompletion(upload, response, new Object(), null);
        interceptor.afterCompletion(download, response, new Object(), null);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }
}
