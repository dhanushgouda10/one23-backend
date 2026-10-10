package com.one23.one23.controller;

import com.one23.one23.dto.LoginRequest;
import com.one23.one23.dto.SignupRequest;
import com.one23.one23.security.LoginRateLimiter;
import com.one23.one23.security.JwtService;
import com.one23.one23.service.EmailService;
import com.one23.one23.model.User;
import com.one23.one23.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

// Controller for user authentication (signup and login)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          EmailService emailService,
                          LoginRateLimiter loginRateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.loginRateLimiter = loginRateLimiter;
    }

    // Register a new user
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {

        String email = normalizeEmail(request.getEmail());

        // Check if email already exists (ignoring upper/lower case)
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email already registered"));
        }

        // Create new user object
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(email); // new users are always saved in lower case

        // Encrypt password before saving
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(encodedPassword);

        // Save user to database
        userRepository.save(user);

        // Best-effort welcome email (runs in background, never fails signup)
        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());

        return ResponseEntity.ok(
                Map.of("message", "Signup successful")
        );
    }

    // Login and return JWT token.
    // Rate limited per client IP and per email (see LoginRateLimiter).
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                    HttpServletRequest httpRequest) {

        String clientIp = httpRequest.getRemoteAddr();
        String email = normalizeEmail(request.getEmail());

        boolean ipAllowed = loginRateLimiter.tryAcquire("ip:" + clientIp);
        boolean emailAllowed = loginRateLimiter.tryAcquire("email:" + email);

        if (!ipAllowed || !emailAllowed) {
            logger.warn("Login rate limit exceeded for ip={} email={}", clientIp, email);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many login attempts. Please try again later."));
        }

        // Same message for unknown email and wrong password,
        // so nobody can find out which emails are registered.
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean credentialsValid = user != null && passwordEncoder.matches(request.getPassword(), user.getPassword());

        // 401 Unauthorized = "we don't know who you are" (wrong email or password)
        if (!credentialsValid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password"));
        }

        // Generate JWT token for this user
        String token = jwtService.generateToken(user.getEmail());

        return ResponseEntity.ok(
                Map.of(
                        "message", "Login successful",
                        "token", token,
                        "email", user.getEmail(),
                        "fullName", user.getFullName()
                )
        );
    }

    // "  Ravi@Gmail.com " -> "ravi@gmail.com"
    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
