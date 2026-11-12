package com.ledgerline.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@SuppressWarnings("unused")
public class ApiExceptionHandler {
    @ExceptionHandler(ApiExceptions.NotFound.class)
    ResponseEntity<Map<String, String>> notFound(ApiExceptions.NotFound exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ApiExceptions.Forbidden.class)
    ResponseEntity<Map<String, String>> forbidden(ApiExceptions.Forbidden exception) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(ApiExceptions.Conflict.class)
    ResponseEntity<Map<String, String>> conflict(ApiExceptions.Conflict exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ApiExceptions.TooManyRequests.class)
    ResponseEntity<Map<String, String>> tooManyRequests(ApiExceptions.TooManyRequests exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(exception.getRetryAfterSeconds()))
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validationException) {
            FieldError fieldError = validationException.getBindingResult().getFieldErrors().stream()
                    .findFirst().orElse(null);
            return response(HttpStatus.BAD_REQUEST,
                    fieldError == null ? "Request validation failed" : fieldError.getDefaultMessage());
        }
        if (exception == null) {
            return response(HttpStatus.BAD_REQUEST, "Invalid request");
        }
        String message = exception.getMessage();
        return response(HttpStatus.BAD_REQUEST, message == null ? "Invalid request" : message);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, String>> unauthorized(BadCredentialsException exception) {
        return response(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> duplicate(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "The request conflicts with an existing record");
    }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message == null ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(body);
    }
}
