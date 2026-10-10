package com.one23.one23.dto;

import jakarta.validation.constraints.NotBlank;

// Request body for chat messages sent via WebSocket
public class ChatMessageRequest {

    @NotBlank(message = "Message cannot be empty")
    private String message;

    public ChatMessageRequest() {
    }

    public ChatMessageRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
