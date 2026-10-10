package com.one23.one23.controller;

import com.one23.one23.dto.ChatMessageRequest;
import com.one23.one23.model.ChatMessage;
import com.one23.one23.service.ChatService;
import com.one23.one23.service.RideService;
import com.one23.one23.dto.LocationUpdate;
import com.one23.one23.model.User;
import com.one23.one23.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Group lobby endpoints: chat history (REST), chat and live location (WebSocket).
// CORS is configured globally in CorsConfig.
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final RideService rideService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatService chatService,
                          RideService rideService,
                          UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.rideService = rideService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // GET /api/chat/{groupId} - load chat history for group lobby
    @GetMapping("/{groupId}")
    public ResponseEntity<?> getChatHistory(@PathVariable String groupId,
                                          Authentication authentication) {

        User user = getLoggedInUser(authentication);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please login first"));
        }

        // Only group members can read chat
        if (!rideService.isActiveMember(groupId, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You are not part of this group"));
        }

        List<ChatMessage> messages = chatService.getGroupMessages(groupId);
        return ResponseEntity.ok(messages);
    }

    // WebSocket: receive message from /app/chat/{groupId}
    // Broadcast saved message to /topic/chat/{groupId}
    @MessageMapping("/chat/{groupId}")
    public void handleChatMessage(@DestinationVariable String groupId,
                                  @Payload @Valid ChatMessageRequest request,
                                  Authentication authentication) {

        User user = getLoggedInUser(authentication);

        if (user == null) {
            return;
        }

        // Only group members can send chat
        if (!rideService.isActiveMember(groupId, user)) {
            return;
        }

        // Use logged-in user's full name automatically
        String senderName = user.getFullName();

        ChatMessage savedMessage = chatService.saveMessage(
                groupId,
                senderName,
                request.getMessage()
        );

        messagingTemplate.convertAndSend("/topic/chat/" + groupId, savedMessage);
    }

    // WebSocket: receive location update from /app/location/{groupId}
    // Broadcast location to /topic/location/{groupId}
    @MessageMapping("/location/{groupId}")
    public void handleLocationUpdate(@DestinationVariable String groupId,
                                     @Payload @Valid LocationUpdate locationUpdate,
                                     Authentication authentication) {

        User user = getLoggedInUser(authentication);

        if (user == null) {
            return;
        }

        // Only group members can share location
        if (!rideService.isActiveMember(groupId, user)) {
            return;
        }

        // Add user's name to location update
        locationUpdate.setUserName(user.getFullName());

        // Broadcast location to group members
        messagingTemplate.convertAndSend("/topic/location/" + groupId, locationUpdate);
    }

    // Helper: get User object from JWT email
    private User getLoggedInUser(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email).orElse(null);
    }
}
