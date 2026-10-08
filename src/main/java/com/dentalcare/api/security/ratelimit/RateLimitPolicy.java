package com.dentalcare.api.security.ratelimit;

public enum RateLimitPolicy {
    LOGIN,
    PASSWORD_RECOVERY,
    PUBLIC_CONTACT,
    API_READ,
    API_WRITE,
    REPORTS
}
