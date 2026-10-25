
package com.one23.one23.ai;

import com.one23.one23.dto.RideParseRequest;
import com.one23.one23.dto.RideParseResponse;
import com.one23.one23.security.LoginRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class RideParserController {

    private final RideParserService rideParserService;

    // Reuses the existing in-memory rate limiter class (a fixed 60-second window),
    // with its own limit from ai.rate-limit.per-minute (default 10).
    private final LoginRateLimiter rateLimiter;

    public RideParserController(
            RideParserService rideParserService,
            @Value("${ai.rate-limit.per-minute:10}") int maxPerMinute) {
        this.rideParserService = rideParserService;
        this.rateLimiter = new LoginRateLimiter(maxPerMinute, 60);
    }

    @PostMapping("/parse-ride")
    public ResponseEntity<?> parseRide(
            @RequestBody RideParseRequest request,
            Authentication authentication) {

        // JWT is already required by SecurityConfig, so we always know the user's email.
        if (!rateLimiter.tryAcquire("ai:" + authentication.getName())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many AI requests. Please try again in a minute."));
        }

        return ResponseEntity.ok(rideParserService.parseRideRequest(request));
    }
}
