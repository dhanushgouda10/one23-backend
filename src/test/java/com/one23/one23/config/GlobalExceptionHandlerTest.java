package com.one23.one23.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadRequest_returnsExceptionMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleBadRequest(new IllegalArgumentException("pickupHub is required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "pickupHub is required");
    }

    @Test
    void handleBadRequest_fallsBackToGenericMessageWhenNull() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleBadRequest(new IllegalStateException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Request could not be processed");
    }

    @Test
    void handleUnexpected_returns500WithoutLeakingExceptionDetail() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpected(new RuntimeException("duplicate key value violates unique constraint \"uk_users_email\""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // The whole point of this handler: never echo the real exception
        // message (which can contain internal detail like table/constraint
        // names) back to the client.
        assertThat(response.getBody()).containsEntry("message", "Something went wrong. Please try again.");
        assertThat(response.getBody().toString()).doesNotContain("uk_users_email");
    }

    @Test
    void nullPointer_isHandledAsUnexpected500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpected(new NullPointerException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "Something went wrong. Please try again.");
    }
}
