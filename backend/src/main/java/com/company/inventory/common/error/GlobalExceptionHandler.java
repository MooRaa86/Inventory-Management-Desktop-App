package com.company.inventory.common.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), null);
    }

    /**
     * Business rules enforced inside request-record constructors surface here
     * wrapped by Jackson; unwrap so clients still receive 4xx + business codes.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        while (cause != null && !(cause instanceof ApiException)) {
            cause = cause.getCause();
        }
        if (cause instanceof ApiException apiEx) {
            return build(apiEx.getStatus(), apiEx.getCode(), apiEx.getMessage(), null);
        }
        return build(400, "MALFORMED_REQUEST", "Request body could not be parsed.", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return build(400, "VALIDATION_ERROR", "Request validation failed", fields);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(409, "CONSTRAINT_VIOLATION",
                "The operation violates a data constraint (duplicate or referenced value).", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "Authentication required.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "You do not have permission to perform this action.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(404, "NOT_FOUND", "Resource not found.", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(400, "BAD_REQUEST", "Invalid parameter: " + ex.getName(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(500, "INTERNAL_ERROR", "An unexpected error occurred. See application logs for details.", null);
    }

    private ResponseEntity<ErrorResponse> build(int status, String code, String message, Map<String, String> fieldErrors) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status, code, message, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
