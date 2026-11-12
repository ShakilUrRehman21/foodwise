package com.foodwise.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standard response envelope (SYSTEM_DESIGN 5.2). Every API response — success or error — uses this shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, ApiError error, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null, Instant.now());
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, List<String>> details) {
        return new ApiResponse<>(false, null, null, new ApiError(code, message, details), Instant.now());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ApiError(String code, String message, Map<String, List<String>> details) {
    }
}
