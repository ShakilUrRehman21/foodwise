package com.foodwise.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps every exception to the {@link ApiResponse} error envelope (SYSTEM_DESIGN 11.1).
 * Internal details are logged, never returned to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Server error: {}", ex.getMessage(), ex);
        } else {
            log.debug("Request rejected [{}]: {}", ex.getCode(), ex.getMessage());
        }
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        Map<String, List<String>> details = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.computeIfAbsent(fe.getField(), k -> new ArrayList<>()).add(fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> details.computeIfAbsent(ge.getObjectName(), k -> new ArrayList<>()).add(ge.getDefaultMessage()));
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed.", details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, List<String>> details = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> details.put(
                r.getMethodParameter().getParameterName() == null ? "param" : r.getMethodParameter().getParameterName(),
                r.getResolvableErrors().stream().map(e -> String.valueOf(e.getDefaultMessage())).toList()));
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed.", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        Map<String, List<String>> details = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                details.computeIfAbsent(v.getPropertyPath().toString(), k -> new ArrayList<>()).add(v.getMessage()));
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed.", details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        String message = switch (ex) {
            case MethodArgumentTypeMismatchException m -> "Invalid value for parameter '" + m.getName() + "'.";
            case MissingServletRequestParameterException m -> "Missing required parameter '" + m.getParameterName() + "'.";
            case MissingServletRequestPartException m -> "Missing required file part '" + m.getRequestPartName() + "'.";
            default -> "Malformed request body.";
        };
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Image is too large. Maximum size is 10 MB.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "The change conflicts with existing data.", Map.of());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleLock(PessimisticLockingFailureException ex) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", "This item is being updated by someone else. Please retry.", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource.", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Endpoint not found.", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method not supported for this endpoint.", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side. Please try again.", Map.of());
    }

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String code, String message, Map<String, List<String>> details) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message, details));
    }
}
