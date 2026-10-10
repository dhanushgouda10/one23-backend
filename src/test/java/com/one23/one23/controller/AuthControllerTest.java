package com.one23.one23.controller;

import com.one23.one23.dto.LoginRequest;
import com.one23.one23.dto.SignupRequest;
import com.one23.one23.security.LoginRateLimiter;
import com.one23.one23.security.JwtService;
import com.one23.one23.service.EmailService;
import com.one23.one23.model.User;
import com.one23.one23.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailService emailService;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @Mock
    private HttpServletRequest httpServletRequest;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(userRepository, passwordEncoder, jwtService, emailService, loginRateLimiter);
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

    private SignupRequest signupRequest(String fullName, String email, String password) {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "fullName", fullName);
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

    // ---- signup ----

    @Test
    void signup_rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("rider@example.com")).thenReturn(true);

        ResponseEntity<?> response = authController.signup(signupRequest("Rider", "rider@example.com", "password123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void signup_savesUserAndSendsWelcomeEmailOnSuccess() {
        when(userRepository.existsByEmailIgnoreCase("rider@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");

        ResponseEntity<?> response = authController.signup(signupRequest("Rider One", "rider@example.com", "password123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("rider@example.com")
                        && user.getFullName().equals("Rider One")
                        && user.getPassword().equals("encoded-hash")
        ));
        verify(emailService).sendWelcomeEmail("rider@example.com", "Rider One");
    }

    @Test
    void signup_savesEmailTrimmedAndLowerCase() {
        when(userRepository.existsByEmailIgnoreCase("rider@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");

        authController.signup(signupRequest("Rider One", "  Rider@Example.COM ", "password123"));

        verify(userRepository).save(argThat(user -> user.getEmail().equals("rider@example.com")));
    }

    @Test
    void signup_rejectsSameEmailWithDifferentCase() {
        when(userRepository.existsByEmailIgnoreCase("rider@example.com")).thenReturn(true);

        ResponseEntity<?> response = authController.signup(signupRequest("Rider", "RIDER@example.com", "password123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    // ---- login ----

    @Test
    void login_rejectedWhenRateLimitExceeded() {
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(loginRateLimiter.tryAcquire(anyString())).thenReturn(false);

        ResponseEntity<?> response = authController.login(loginRequest("rider@example.com", "password123"), httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(userRepository);
    }

    @Test
    void login_rejectsUnknownEmail() {
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(loginRateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("rider@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = authController.login(loginRequest("rider@example.com", "password123"), httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_rejectsWrongPassword() {
        User user = new User();
        user.setEmail("rider@example.com");
        user.setPassword("encoded-hash");

        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(loginRateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("rider@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-hash")).thenReturn(false);

        ResponseEntity<?> response = authController.login(loginRequest("rider@example.com", "wrong-password"), httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_returnsTokenOnSuccess() {
        User user = new User();
        user.setEmail("rider@example.com");
        user.setFullName("Rider One");
        user.setPassword("encoded-hash");

        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(loginRateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("rider@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-hash")).thenReturn(true);
        when(jwtService.generateToken("rider@example.com")).thenReturn("signed.jwt.token");

        ResponseEntity<?> response = authController.login(loginRequest("rider@example.com", "password123"), httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toString())
                .contains("signed.jwt.token")
                .contains("rider@example.com")
                .contains("Rider One");
    }

    @Test
    void login_worksWithDifferentCase_andKeepsStoredEmailInToken() {
        // An older user saved before emails were lower-cased
        User user = new User();
        user.setEmail("Rider@Example.com");
        user.setFullName("Rider One");
        user.setPassword("encoded-hash");

        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(loginRateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("rider@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-hash")).thenReturn(true);
        when(jwtService.generateToken("Rider@Example.com")).thenReturn("signed.jwt.token");

        ResponseEntity<?> response = authController.login(loginRequest(" RIDER@example.com ", "password123"), httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(loginRateLimiter).tryAcquire("email:rider@example.com");
    }
}
