package com.st4r4x.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.st4r4x.dto.AuthRequest;
import com.st4r4x.dto.JwtResponse;
import com.st4r4x.dto.RegisterRequest;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.UserRepository;
import com.st4r4x.security.JwtService;
import com.st4r4x.util.ValidationUtil;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtUtil;

    /** Injected from ${controller.signup.code} / CONTROLLER_SIGNUP_CODE env var. Null = disabled. */
    private final String controllerSignupCode;

    /** Injected from ${admin.signup.code} / ADMIN_SIGNUP_CODE env var. Null = disabled. */
    private final String adminSignupCode;

    @Autowired
    public AuthService(
            @Value("${controller.signup.code:#{null}}") String controllerSignupCode,
            @Value("${admin.signup.code:#{null}}") String adminSignupCode) {
        this.controllerSignupCode = controllerSignupCode;
        this.adminSignupCode = adminSignupCode;
    }

    /** Package-visible constructor for unit tests — sets signup codes directly. */
    AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                JwtService jwtUtil, String controllerSignupCode, String adminSignupCode) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.controllerSignupCode = controllerSignupCode;
        this.adminSignupCode = adminSignupCode;
    }

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

        String providedCode = request.getSignupCode();

        String role;
        if (providedCode == null || providedCode.isEmpty()) {
            role = "ROLE_CUSTOMER";
        } else if (adminSignupCode != null && !adminSignupCode.isEmpty()
                   && adminSignupCode.equals(providedCode)) {
            // Admin signup code takes priority over controller code
            role = "ROLE_ADMIN";
        } else {
            // Controller signup is disabled when env var is not set — fail-safe
            if (controllerSignupCode == null || controllerSignupCode.isEmpty()) {
                throw new IllegalArgumentException("Invalid registration request");
            }
            if (!controllerSignupCode.equals(providedCode)) {
                throw new IllegalArgumentException("Invalid registration request");
            }
            role = "ROLE_CONTROLLER";
        }
        UserEntity userEntity = new UserEntity(request.getUsername(), request.getEmail(), hash, role);
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
