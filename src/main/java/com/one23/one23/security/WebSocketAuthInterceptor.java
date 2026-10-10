package com.one23.one23.security;

import com.one23.one23.model.User;
import com.one23.one23.repository.UserRepository;
import com.one23.one23.service.RideService;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

// Checks every STOMP frame coming from the browser:
//  - CONNECT:   reads the JWT so chat/location messages know which user is logged in
//  - SUBSCRIBE: only members of a group may listen to that group's chat, location and events
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    // Private per-group topics. The text after the prefix is the groupId.
    private static final List<String> GROUP_TOPIC_PREFIXES =
            List.of("/topic/chat/", "/topic/location/", "/topic/group/");

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RideService rideService;

    // @Lazy breaks a startup cycle: WebSocketConfig -> this interceptor -> RideService
    // -> SimpMessagingTemplate (which is created by the WebSocket configuration).
    public WebSocketAuthInterceptor(JwtService jwtService,
                                    UserRepository userRepository,
                                    @Lazy RideService rideService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.rideService = rideService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractBearerToken(accessor);
            if (token != null) {
                authenticateStompUser(token, accessor);
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            checkSubscription(accessor.getDestination(), accessor.getUser());
        }

        return message;
    }

    // Throwing here makes Spring reject the SUBSCRIBE and send an ERROR frame to the client.
    private void checkSubscription(String destination, Principal principal) {
        if (destination == null) {
            throw new MessageDeliveryException("Subscription destination is missing");
        }

        // The simple broker treats * and ? as wildcards, so "/topic/**" would receive
        // every group's chat. Only exact destinations are allowed.
        if (destination.contains("*") || destination.contains("?") || destination.contains("{")) {
            throw new MessageDeliveryException("Wildcard subscriptions are not allowed");
        }

        for (String prefix : GROUP_TOPIC_PREFIXES) {
            if (destination.startsWith(prefix)) {
                String groupId = destination.substring(prefix.length());
                User user = principal == null ? null
                        : userRepository.findByEmail(principal.getName()).orElse(null);

                if (!rideService.isActiveMember(groupId, user)) {
                    throw new MessageDeliveryException("You are not part of this group");
                }
                return;
            }
        }
        // Other topics (e.g. /topic/match, which carries no private data) are allowed.
    }

    // Reads "Authorization: Bearer <token>" from the CONNECT frame headers
    private String extractBearerToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring("Bearer ".length());
    }

    // If the token is valid, attach the user's email to this WebSocket session.
    // Later frames (SUBSCRIBE, SEND) on the same connection carry this user.
    private void authenticateStompUser(String token, StompHeaderAccessor accessor) {
        try {
            String email = jwtService.extractEmail(token);
            if (email == null || !jwtService.isTokenValid(token, email)) {
                return;
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());

            accessor.setUser(authToken);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (RuntimeException ex) {
            // Expired or forged token: connect as anonymous (private topics will be refused)
        }
    }
}
