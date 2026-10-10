package com.one23.one23.service;

import com.one23.one23.model.ChatMessage;
import com.one23.one23.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// Saves and loads group chat messages.
// (Checking that a user belongs to the group is done by RideService.isActiveMember.)
@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    // Save a new chat message
    public ChatMessage saveMessage(String groupId, String senderName, String messageText) {

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setGroupId(groupId);
        chatMessage.setSenderName(senderName);
        chatMessage.setMessage(messageText);
        chatMessage.setTimestamp(LocalDateTime.now());

        return chatMessageRepository.save(chatMessage);
    }

    // Get all previous messages for one group (oldest first)
    public List<ChatMessage> getGroupMessages(String groupId) {
        return chatMessageRepository.findByGroupIdOrderByTimestampAsc(groupId);
    }
}
