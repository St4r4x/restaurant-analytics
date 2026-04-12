package com.st4r4x.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            JwtResponse response = authService.login(request);
            return ResponseEntity.ok(response);
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

    private Object errorResponse(Exception e) {
        return new Object() {
            public final String status = "error";
            public final String message = e.getMessage();
        };
    }
}
