package com.dentalcare.api.security.ratelimit;

public enum RateLimitPolicy {
    LOGIN,
    PASSWORD_RECOVERY,
    PUBLIC_CONTACT,
    PUBLIC_APPOINTMENT_REQUEST,
    PUBLIC_ASSISTANT,
    API_READ,
    API_WRITE,
    CLINICAL_DOCUMENT_UPLOAD,
    CLINICAL_DOCUMENT_DOWNLOAD,
    REPORTS
}
