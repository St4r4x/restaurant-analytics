package com.st4r4x.controller;

import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.st4r4x.config.AppConfig;
import com.st4r4x.dto.AuthRequest;
import com.st4r4x.dto.JwtResponse;
import com.st4r4x.dto.RefreshRequest;
import com.st4r4x.dto.RegisterRequest;
import com.st4r4x.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            JwtResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        try {
            JwtResponse tokens = authService.login(request);
            setAuthCookies(response, tokens.getAccessToken(), tokens.getRefreshToken());
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            JwtResponse response = authService.refresh(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }

    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        try {
            boolean available = authService.isUsernameAvailable(username);
            return ResponseEntity.ok(Map.of("available", available));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }

    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        try {
            boolean available = authService.isEmailAvailable(email);
            return ResponseEntity.ok(Map.of("available", available));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }

    private Object errorResponse(Exception e) {
        return new Object() {
            public final String status = "error";
            public final String message = e.getMessage();
        };
    }

    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        Cookie accessCookie = new Cookie("access_token", accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(AppConfig.isCookieSecure());
        accessCookie.setPath("/");
        accessCookie.setMaxAge((int) (AppConfig.getJwtAccessTokenExpirationMs() / 1000));
        accessCookie.setAttribute("SameSite", "Strict");
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(AppConfig.isCookieSecure());
        refreshCookie.setPath("/api/auth/");
        refreshCookie.setMaxAge((int) (AppConfig.getJwtRefreshTokenExpirationMs() / 1000));
        refreshCookie.setAttribute("SameSite", "Strict");
        response.addCookie(refreshCookie);
    }

    private void clearAuthCookies(HttpServletResponse response) {
        Cookie accessCookie = new Cookie("access_token", "");
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(AppConfig.isCookieSecure());
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie("refresh_token", "");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(AppConfig.isCookieSecure());
        refreshCookie.setPath("/api/auth/");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);
    }
}
