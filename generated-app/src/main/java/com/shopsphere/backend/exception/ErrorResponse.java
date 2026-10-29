package com.shopsphere.backend.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
    int status,
    String message,
    LocalDateTime timestamp,
    Map<String, String> fieldErrors
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, LocalDateTime.now(), null);
    }

    public static ErrorResponse validation(int status, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, message, LocalDateTime.now(), fieldErrors);
    }
}
