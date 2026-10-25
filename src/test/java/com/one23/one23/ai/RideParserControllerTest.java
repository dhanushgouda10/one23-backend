package com.one23.one23.ai;

import com.one23.one23.dto.RideParseRequest;
import com.one23.one23.dto.RideParseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Plain unit test: the service is mocked, so Gemini is never called.
class RideParserControllerTest {

    private Authentication user(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
    }

    @Test
    void requestsOverTheLimit_getTooManyRequests() {
        RideParserService service = mock(RideParserService.class);
        when(service.parseRideRequest(any())).thenReturn(new RideParseResponse());
        RideParserController controller = new RideParserController(service, 2);

        ResponseEntity<?> first = controller.parseRide(new RideParseRequest(), user("a@test.com"));
        ResponseEntity<?> second = controller.parseRide(new RideParseRequest(), user("a@test.com"));
        ResponseEntity<?> third = controller.parseRide(new RideParseRequest(), user("a@test.com"));

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(service, times(2)).parseRideRequest(any()); // blocked call never reaches Gemini
    }

    @Test
    void limitIsPerUser() {
        RideParserService service = mock(RideParserService.class);
        when(service.parseRideRequest(any())).thenReturn(new RideParseResponse());
        RideParserController controller = new RideParserController(service, 1);

        controller.parseRide(new RideParseRequest(), user("a@test.com"));
        ResponseEntity<?> other = controller.parseRide(new RideParseRequest(), user("b@test.com"));

        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
