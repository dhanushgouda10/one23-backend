package com.one23.one23.config;

import com.one23.one23.security.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    // Same allowed origins as the REST API (see CorsConfig)
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    // Configure STOMP message broker
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {

        // Frontend subscribes to /topic/*
        config.enableSimpleBroker("/topic");

        // Frontend sends messages to /app/*
        config.setApplicationDestinationPrefixes("/app");
    }

    // Read the JWT from the STOMP CONNECT frame
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }

    // WebSocket connection endpoint for frontend
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}
