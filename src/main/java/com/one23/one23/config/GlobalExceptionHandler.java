package com.one23.one23.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

// Central error handler: every API error returns JSON like
//   { "message": "...", "fieldErrors": { "email": "..." } }
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Bean Validation failures (@Valid on @RequestBody DTOs like SignupRequest / LoginRequest)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        String firstMessage = firstFieldErrorMessage(fieldErrors);

        Map<String, Object> body = new HashMap<>();
        body.put("message", firstMessage);
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    // Top-level "message" shows the first field error
    private String firstFieldErrorMessage(Map<String, String> fieldErrors) {
        for (String message : fieldErrors.values()) {
            return message;
        }
        return "Validation failed";
    }

    // Expected "bad input" errors thrown on purpose (e.g. MatchingService) -> 400
    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Request could not be processed");
        return ResponseEntity.badRequest().body(body);
    }

    // Anything unexpected (including NullPointerException, which is a real bug):
    // log it on the server, return a generic 500 to the client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        logger.error("Unhandled exception while processing request", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Something went wrong. Please try again.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
