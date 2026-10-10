package com.one23.one23.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    @Test
    void allowsUpToConfiguredAttemptsWithinWindow() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 60);

        assertThat(limiter.tryAcquire("ip:1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("ip:1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("ip:1.2.3.4")).isTrue();
    }

    @Test
    void rejectsOnceLimitExceededWithinWindow() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 60);

        limiter.tryAcquire("ip:1.2.3.4");
        limiter.tryAcquire("ip:1.2.3.4");
        limiter.tryAcquire("ip:1.2.3.4");

        assertThat(limiter.tryAcquire("ip:1.2.3.4")).isFalse();
    }

    @Test
    void tracksKeysIndependently() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, 60);

        assertThat(limiter.tryAcquire("ip:1.2.3.4")).isTrue();
        // A different key (e.g. a different IP, or the email-keyed limiter)
        // has its own independent budget.
        assertThat(limiter.tryAcquire("ip:5.6.7.8")).isTrue();
        // But the first key is now over its limit.
        assertThat(limiter.tryAcquire("ip:1.2.3.4")).isFalse();
    }

    @Test
    void resetsAfterWindowElapses() throws InterruptedException {
        LoginRateLimiter limiter = new LoginRateLimiter(1, 0);

        assertThat(limiter.tryAcquire("ip:1.2.3.4")).isTrue();
        // window-seconds=0 means the window is already elapsed by the next call
        Thread.sleep(5);
        assertThat(limiter.tryAcquire("ip:1.2.3.4")).isTrue();
    }
}
