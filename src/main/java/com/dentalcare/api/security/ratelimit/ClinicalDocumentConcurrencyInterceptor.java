package com.dentalcare.api.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ClinicalDocumentConcurrencyInterceptor implements HandlerInterceptor {
    private static final String PERMIT_ATTRIBUTE =
            ClinicalDocumentConcurrencyInterceptor.class.getName() + ".permit";

    private final Semaphore uploadPermits;
    private final Semaphore downloadPermits;

    public ClinicalDocumentConcurrencyInterceptor(
            @Value("${dentalcare.clinical-documents.max-concurrent-uploads:4}") int maxUploads,
            @Value("${dentalcare.clinical-documents.max-concurrent-downloads:20}") int maxDownloads) {
        if (maxUploads < 1 || maxDownloads < 1) {
            throw new IllegalArgumentException("Clinical document concurrency limits must be positive");
        }
        this.uploadPermits = new Semaphore(maxUploads, true);
        this.downloadPermits = new Semaphore(maxDownloads, true);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Semaphore permits = select(request.getMethod(), request.getRequestURI());
        if (permits == null) return true;
        if (!permits.tryAcquire()) throw new RateLimitExceededException(1);
        request.setAttribute(PERMIT_ATTRIBUTE, permits);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        Object permit = request.getAttribute(PERMIT_ATTRIBUTE);
        if (permit instanceof Semaphore semaphore) {
            request.removeAttribute(PERMIT_ATTRIBUTE);
            semaphore.release();
        }
    }

    private Semaphore select(String method, String path) {
        if ("POST".equalsIgnoreCase(method)
                && path.matches("^/api/v1/patients/[^/]+/documents/upload$")) return uploadPermits;
        if ("GET".equalsIgnoreCase(method) && path.endsWith("/download")
                && (path.matches("^/api/v1/patients/[^/]+/documents/[^/]+/download$")
                || path.matches("^/api/v1/patients/me/documents/[^/]+/download$"))) return downloadPermits;
        return null;
    }
}
