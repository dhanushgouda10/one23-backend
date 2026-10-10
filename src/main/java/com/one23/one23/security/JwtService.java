package com.one23.one23.security;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// Service for generating and validating JWT tokens
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    // Stop the app at startup if JWT_SECRET is too short.
    // HS256 needs at least 32 bytes; generate one with: openssl rand -base64 48
    @PostConstruct
    void checkSecret() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters long. Generate one with: openssl rand -base64 48");
        }
    }

    // Create secret key from application.properties
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Generate JWT token for logged-in user
    public String generateToken(String email) {

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    // Read email from JWT token
    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // Check if token belongs to this user.
    // No separate expiry check needed: extractEmail() already throws
    // ExpiredJwtException for an expired token (and an exception for a forged one).
    // The callers (JwtAuthenticationFilter, WebSocketAuthInterceptor) catch it.
    public boolean isTokenValid(String token, String email) {
        return extractEmail(token).equals(email);
    }
}
