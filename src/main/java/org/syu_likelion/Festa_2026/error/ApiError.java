package org.syu_likelion.Festa_2026.error;

import java.time.Instant;

public record ApiError(String code, String message, Instant timestamp,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Long retryAfterSeconds) {
    public ApiError(String code, String message, Instant timestamp) {
        this(code, message, timestamp, null);
    }
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now());
    }
}
