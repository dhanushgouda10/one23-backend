package com.one23.one23.security;

import com.one23.one23.model.User;
import com.one23.one23.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null) {
            authenticate(token, request);
        }

        // Always continue — routes that require login are enforced by
        // SecurityConfig, not by this filter. A missing/invalid token
        // just means the request proceeds without an authenticated user.
        filterChain.doFilter(request, response);
    }

    // Pulls the raw JWT out of "Authorization: Bearer <token>", or null
    // if the header is missing/not a Bearer token.
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }

    // Validates the token and, if it checks out, marks the request as
    // authenticated for the rest of the filter chain / controller.
    private void authenticate(String token, HttpServletRequest request) {
        try {
            String email = jwtService.extractEmail(token);
            if (email == null || SecurityContextHolder.getContext().getAuthentication() != null) {
                return;
            }

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null || !jwtService.isTokenValid(token, user.getEmail())) {
                return;
            }

            setAuthenticatedUser(user, request);
        } catch (RuntimeException ex) {
            // Ignore invalid or expired tokens for public routes like signup/login.
            SecurityContextHolder.clearContext();
        }
    }

    private void setAuthenticatedUser(User user, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.emptyList());

        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
