package com.foodwise.common;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Base class for all expected (domain) failures. Mapped to the error envelope by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, List<String>> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, List<String>> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, List<String>> getDetails() {
        return details;
    }

    // ---- Factories for the documented error codes (SYSTEM_DESIGN 5.3) ----

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ApiException conflict(String message, Map<String, List<String>> details) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message, details);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message, Map.of(field, List.of(message)));
    }

    public static ApiException validation(String message, Map<String, List<String>> details) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message, details);
    }

    public static ApiException rateLimited(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
    }
}
