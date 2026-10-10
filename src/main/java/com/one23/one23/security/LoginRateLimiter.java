package com.one23.one23.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Simple in-memory rate limiter for POST /api/auth/login.
// Counts attempts per key (client IP and email) inside a fixed time window
// and rejects once the limit is exceeded. In-memory is fine for one backend instance.
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;

    private final Map<String, Window> attemptsByKey = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.security.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login-rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    // Records one attempt for this key. Returns false if the limit is exceeded.
    public boolean tryAcquire(String key) {
        Instant now = Instant.now();

        Window current = attemptsByKey.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart.plus(window).isBefore(now)) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return current.count.get() <= maxAttempts;
    }

    private static final class Window {
        private final Instant windowStart;
        private final AtomicInteger count;

        private Window(Instant windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
