package com.aflokkat.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.aflokkat.dto.AuthRequest;
import com.aflokkat.dto.JwtResponse;
import com.aflokkat.dto.RegisterRequest;
import com.aflokkat.entity.UserEntity;
import com.aflokkat.repository.UserRepository;
import com.aflokkat.security.JwtUtil;
import com.aflokkat.util.ValidationUtil;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public JwtResponse register(RegisterRequest request) {
        ValidationUtil.requireNonEmpty(request.getUsername(), "username");
        ValidationUtil.requireNonEmpty(request.getEmail(), "email");
        ValidationUtil.requireNonEmpty(request.getPassword(), "password");

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        String hash = passwordEncoder.encode(request.getPassword());
        UserEntity userEntity = new UserEntity(request.getUsername(), request.getEmail(), hash, "ROLE_USER");
        userEntity = userRepository.save(userEntity);

        String accessToken = jwtUtil.generateAccessToken(userEntity.getUsername(), userEntity.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(userEntity.getUsername());
        return new JwtResponse(accessToken, refreshToken);
    }

    public JwtResponse login(AuthRequest request) {
        ValidationUtil.requireNonEmpty(request.getUsername(), "username");
        ValidationUtil.requireNonEmpty(request.getPassword(), "password");

        java.util.Optional<UserEntity> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        UserEntity user = userOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
        return new JwtResponse(accessToken, refreshToken);
    }

    public JwtResponse refresh(String refreshToken) {
        ValidationUtil.requireNonEmpty(refreshToken, "refreshToken");

        io.jsonwebtoken.Claims claims = jwtUtil.getClaimsIfValid(refreshToken);
        if (claims == null) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String username = claims.getSubject();
        java.util.Optional<UserEntity> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        UserEntity user = userOpt.get();

        String newAccessToken = jwtUtil.generateAccessToken(user.getUsername(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getUsername());
        return new JwtResponse(newAccessToken, newRefreshToken);
    }
}
