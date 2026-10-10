package com.one23.one23.repository;

import com.one23.one23.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Repository for chat message database operations
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Get all messages for a specific group, sorted by timestamp (oldest first)
    List<ChatMessage> findByGroupIdOrderByTimestampAsc(String groupId);
}
