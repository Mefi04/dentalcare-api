package com.dentalcare.api.security.ratelimit;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    public static RateLimitDecision permit() { return new RateLimitDecision(true, 0); }
    public static RateLimitDecision blocked(long retryAfterSeconds) { return new RateLimitDecision(false, retryAfterSeconds); }
}
