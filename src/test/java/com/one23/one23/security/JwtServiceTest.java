package com.one23.one23.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // jwtSecret/jwtExpiration are normally injected via @Value from
        // application.properties; set them directly since this is a plain
        // unit test with no Spring context.
        ReflectionTestUtils.setField(jwtService, "jwtSecret",
                "unit-test-only-secret-key-must-be-long-enough-for-hmac-sha256");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86_400_000L);
    }

    @Test
    void generateToken_thenExtractEmail_roundTrips() {
        String token = jwtService.generateToken("rider@example.com");

        assertThat(jwtService.extractEmail(token)).isEqualTo("rider@example.com");
    }

    @Test
    void isTokenValid_trueForMatchingEmailAndUnexpiredToken() {
        String token = jwtService.generateToken("rider@example.com");

        assertThat(jwtService.isTokenValid(token, "rider@example.com")).isTrue();
    }

    @Test
    void isTokenValid_falseForDifferentEmail() {
        String token = jwtService.generateToken("rider@example.com");

        assertThat(jwtService.isTokenValid(token, "someone-else@example.com")).isFalse();
    }

    @Test
    void expiredToken_throwsOnParse() {
        // jwtExpiration is negative, so the token is already expired the
        // instant it's created. This documents actual current behavior:
        // JJWT throws ExpiredJwtException while parsing an expired token,
        // so callers never reach JwtService's own isTokenExpired() check —
        // JwtAuthenticationFilter relies on catching this RuntimeException
        // rather than a false return value. Not changed here since it's
        // existing behavior, just verified.
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -60_000L);
        String expiredToken = jwtService.generateToken("rider@example.com");

        assertThatThrownBy(() -> jwtService.extractEmail(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void isTokenValid_throwsForExpiredToken_soCallersTreatItAsNotLoggedIn() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -60_000L);
        String expiredToken = jwtService.generateToken("rider@example.com");

        assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken, "rider@example.com"))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void isTokenValid_throwsForTokenSignedWithAnotherSecret() {
        String token = jwtService.generateToken("rider@example.com");
        ReflectionTestUtils.setField(jwtService, "jwtSecret",
                "a-completely-different-secret-key-that-is-also-long-enough");

        assertThatThrownBy(() -> jwtService.isTokenValid(token, "rider@example.com"))
                .isInstanceOf(RuntimeException.class);
    }
}
