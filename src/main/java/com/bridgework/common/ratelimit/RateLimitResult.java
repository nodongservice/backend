package com.bridgework.common.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long limit,
        long remaining,
        long retryAfterSeconds,
        long resetAfterSeconds
) {
}
