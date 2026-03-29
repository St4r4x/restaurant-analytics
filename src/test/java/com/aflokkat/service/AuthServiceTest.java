package com.aflokkat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.aflokkat.config.AppConfig;
import com.aflokkat.dto.AuthRequest;
import com.aflokkat.dto.JwtResponse;
import com.aflokkat.dto.RegisterRequest;
import com.aflokkat.entity.UserEntity;
import com.aflokkat.repository.UserRepository;
import com.aflokkat.security.JwtUtil;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_returnsTokens_onSuccess() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        UserEntity saved = new UserEntity("alice", "alice@example.com", "hashed", "ROLE_CUSTOMER");
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);
        when(jwtUtil.generateAccessToken("alice", "ROLE_CUSTOMER")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("alice")).thenReturn("refresh-token");

        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        try (MockedStatic<AppConfig> appConfig = mockStatic(AppConfig.class)) {
            appConfig.when(AppConfig::getControllerSignupCode).thenReturn(null);
            JwtResponse response = authService.register(req);
            assertEquals("access-token", response.getAccessToken());
            assertEquals("refresh-token", response.getRefreshToken());
        }
    }

    @Test
    void register_throws_whenUsernameAlreadyExists() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new UserEntity()));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        assertThrows(IllegalArgumentException.class, () -> authService.register(req));
    }

    @Test
    void register_throws_whenEmailAlreadyExists() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(new UserEntity()));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        assertThrows(IllegalArgumentException.class, () -> authService.register(req));
    }

    @Test
    void register_throws_whenUsernameIsBlank() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        assertThrows(IllegalArgumentException.class, () -> authService.register(req));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_returnsTokens_onValidCredentials() {
        UserEntity user = new UserEntity("bob", "bob@example.com", "hashed", "ROLE_USER");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(jwtUtil.generateAccessToken("bob", "ROLE_USER")).thenReturn("access");
        when(jwtUtil.generateRefreshToken("bob")).thenReturn("refresh");

        AuthRequest req = new AuthRequest();
        req.setUsername("bob");
        req.setPassword("password");

        JwtResponse response = authService.login(req);

        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());
    }

    @Test
    void login_throws_whenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        AuthRequest req = new AuthRequest();
        req.setUsername("unknown");
        req.setPassword("password");

        assertThrows(IllegalArgumentException.class, () -> authService.login(req));
    }

    @Test
    void login_throws_whenPasswordIsWrong() {
        UserEntity user = new UserEntity("bob", "bob@example.com", "hashed", "ROLE_USER");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        AuthRequest req = new AuthRequest();
        req.setUsername("bob");
        req.setPassword("wrong");

        assertThrows(IllegalArgumentException.class, () -> authService.login(req));
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_returnsNewTokens_forValidRefreshToken() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn("carol");
        when(jwtUtil.getClaimsIfValid("valid-refresh")).thenReturn(claims);

        UserEntity user = new UserEntity("carol", "carol@example.com", "hashed", "ROLE_ADMIN");
        when(userRepository.findByUsername("carol")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken("carol", "ROLE_ADMIN")).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken("carol")).thenReturn("new-refresh");

        JwtResponse response = authService.refresh("valid-refresh");

        assertEquals("new-access", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
    }

    @Test
    void refresh_throws_whenTokenIsInvalid() {
        when(jwtUtil.getClaimsIfValid("bad-token")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> authService.refresh("bad-token"));
    }

    @Test
    void refresh_throws_whenTokenIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> authService.refresh(""));
    }

    // ── role-assignment ───────────────────────────────────────────────────────

    @Test
    void register_assignsCustomerRole_whenNoSignupCode() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateAccessToken(any(), any())).thenReturn("token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("pass");
        // signupCode NOT set (null)

        try (MockedStatic<AppConfig> appConfig = mockStatic(AppConfig.class)) {
            appConfig.when(AppConfig::getControllerSignupCode).thenReturn(null);
            authService.register(req);
        }

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals("ROLE_CUSTOMER", captor.getValue().getRole());
    }

    @Test
    void register_assignsControllerRole_whenCorrectSignupCode() {
        when(userRepository.findByUsername("ctrl")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ctrl@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateAccessToken(any(), any())).thenReturn("token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        RegisterRequest req = new RegisterRequest();
        req.setUsername("ctrl");
        req.setEmail("ctrl@test.com");
        req.setPassword("pass");
        req.setSignupCode("secret123");

        try (MockedStatic<AppConfig> appConfig = mockStatic(AppConfig.class)) {
            appConfig.when(AppConfig::getControllerSignupCode).thenReturn("secret123");
            authService.register(req);
        }

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals("ROLE_CONTROLLER", captor.getValue().getRole());
    }

    @Test
    void register_throws_whenWrongSignupCode() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("pass");
        req.setSignupCode("wrongcode");

        try (MockedStatic<AppConfig> appConfig = mockStatic(AppConfig.class)) {
            appConfig.when(AppConfig::getControllerSignupCode).thenReturn("secret123");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> authService.register(req));
            assertEquals("Invalid registration request", ex.getMessage());
        }
    }

    @Test
    void register_throws_whenSignupCodeEnvVarAbsent() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("pass");
        req.setSignupCode("anything");

        try (MockedStatic<AppConfig> appConfig = mockStatic(AppConfig.class)) {
            appConfig.when(AppConfig::getControllerSignupCode).thenReturn(null);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> authService.register(req));
            assertEquals("Invalid registration request", ex.getMessage());
        }
    }
}
