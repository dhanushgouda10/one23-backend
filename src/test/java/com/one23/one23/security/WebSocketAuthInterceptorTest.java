package com.one23.one23.security;

import com.one23.one23.model.User;
import com.one23.one23.repository.UserRepository;
import com.one23.one23.service.RideService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RideService rideService;

    private Message<byte[]> subscribe(String destination, String email) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (email != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList()));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private WebSocketAuthInterceptor interceptor() {
        return new WebSocketAuthInterceptor(jwtService, userRepository, rideService);
    }

    @Test
    void memberCanSubscribeToOwnGroupChat() {
        User alice = new User();
        when(userRepository.findByEmail("alice@x.com")).thenReturn(Optional.of(alice));
        when(rideService.isActiveMember("g1", alice)).thenReturn(true);

        assertThatCode(() -> interceptor().preSend(subscribe("/topic/chat/g1", "alice@x.com"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void nonMemberCannotSubscribeToAnotherGroup() {
        User mallory = new User();
        when(userRepository.findByEmail("mallory@x.com")).thenReturn(Optional.of(mallory));
        when(rideService.isActiveMember("g1", mallory)).thenReturn(false);

        assertThatThrownBy(() -> interceptor().preSend(subscribe("/topic/location/g1", "mallory@x.com"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void anonymousCannotSubscribeToGroupTopics() {
        when(rideService.isActiveMember("g1", null)).thenReturn(false);

        assertThatThrownBy(() -> interceptor().preSend(subscribe("/topic/group/g1", null), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void wildcardSubscriptionsAreRejected() {
        assertThatThrownBy(() -> interceptor().preSend(subscribe("/topic/**", "alice@x.com"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void matchTopicIsAllowedForEveryone() {
        assertThatCode(() -> interceptor().preSend(subscribe("/topic/match", null), null))
                .doesNotThrowAnyException();
    }
}
