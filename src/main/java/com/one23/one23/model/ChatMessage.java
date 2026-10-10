package com.one23.one23.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

// Entity representing a chat message in a group.
// Index on (group_id, timestamp) for loading chat history in order.
@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_messages_group_id_timestamp", columnList = "group_id, timestamp")
        }
)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Group ID is required")
    @Column(nullable = false)
    private String groupId;

    @NotBlank(message = "Sender name is required")
    @Column(nullable = false)
    private String senderName;

    @NotBlank(message = "Message text is required")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public ChatMessage() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
