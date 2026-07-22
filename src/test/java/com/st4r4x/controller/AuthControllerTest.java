package com.st4r4x.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.st4r4x.dto.AuthRequest;
import com.st4r4x.dto.JwtResponse;
import com.st4r4x.dto.RefreshRequest;
import com.st4r4x.dto.RegisterRequest;
import com.st4r4x.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @InjectMocks
    private AuthController authController;

    @Mock
    private AuthService authService;

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_returns200_onSuccess() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");
        when(authService.register(req)).thenReturn(new JwtResponse("access", "refresh"));

        ResponseEntity<?> response = authController.register(req);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(JwtResponse.class, response.getBody());
    }

    @Test
    void register_returns400_onIllegalArgument() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        when(authService.register(req)).thenThrow(new IllegalArgumentException("Username already exists"));

        ResponseEntity<?> response = authController.register(req);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void register_returns500_onUnexpectedError() {
        RegisterRequest req = new RegisterRequest();
        when(authService.register(req)).thenThrow(new RuntimeException("DB unavailable"));

        ResponseEntity<?> response = authController.register(req);

        assertEquals(500, response.getStatusCode().value());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_returns200_onValidCredentials() {
        AuthRequest req = new AuthRequest();
        req.setUsername("bob");
        req.setPassword("password");
        when(authService.login(req)).thenReturn(new JwtResponse("access", "refresh"));

        ResponseEntity<?> response = authController.login(req);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void login_returns400_onInvalidCredentials() {
        AuthRequest req = new AuthRequest();
        req.setUsername("bob");
        req.setPassword("wrong");
        when(authService.login(req)).thenThrow(new IllegalArgumentException("Invalid credentials"));

        ResponseEntity<?> response = authController.login(req);

        assertEquals(400, response.getStatusCode().value());
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_returns200_onValidToken() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("valid-refresh");
        when(authService.refresh("valid-refresh")).thenReturn(new JwtResponse("new-access", "new-refresh"));

        ResponseEntity<?> response = authController.refresh(req);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void refresh_returns400_onExpiredOrInvalidToken() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("expired-token");
        when(authService.refresh("expired-token")).thenThrow(new IllegalArgumentException("Invalid refresh token"));

        ResponseEntity<?> response = authController.refresh(req);

        assertEquals(400, response.getStatusCode().value());
    }
}
