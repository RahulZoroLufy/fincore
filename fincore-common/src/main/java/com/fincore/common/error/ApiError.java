package com.fincore.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Canonical error response returned by every FinCore HTTP service.
 * Shape is stable across services so clients can rely on it.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,          // short machine-readable code, e.g. "NOT_FOUND"
        String message,        // human-readable summary
        String path,           // request URI
        String correlationId,  // request correlation id, if present
        List<FieldError> details
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(int status,
    String error,
    String message,
    String path,
    String correlationId) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, List.of());
    }

    public static ApiError of(int status,
    String error,
    String message,
    String path,
    String correlationId,
    List<FieldError> details) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, details);
    }
}