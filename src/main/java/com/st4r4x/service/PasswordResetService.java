package com.st4r4x.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.st4r4x.entity.PasswordResetTokenEntity;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.PasswordResetTokenRepository;
import com.st4r4x.repository.UserRepository;
import com.st4r4x.util.ValidationUtil;

@Service
public class PasswordResetService {

    private static final long TOKEN_VALIDITY_MS = 3_600_000L; // 1 hour
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                 EmailService emailService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Always succeeds with no observable difference between "email exists" and
     * "email doesn't exist" — anti-enumeration by design. Never throws for a
     * missing email; only a genuinely unexpected failure (e.g. email delivery
     * error) propagates.
     */
    public void requestReset(String email, String appBaseUrl) {
        Optional<UserEntity> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return;
        }
        UserEntity user = userOpt.get();

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        Date expiresAt = new Date(System.currentTimeMillis() + TOKEN_VALIDITY_MS);

        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity(user, tokenHash, expiresAt);
        tokenRepository.save(tokenEntity);

        String resetLink = appBaseUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        try {
            ValidationUtil.requireNonEmpty(rawToken, "token");
        } catch (IllegalArgumentException e) {
            // Same message as an unknown-token lookup below — a missing token
            // must be indistinguishable from an invalid one to the caller.
            throw new IllegalArgumentException("Invalid or expired reset link");
        }

        String tokenHash = hashToken(rawToken);
        PasswordResetTokenEntity tokenEntity = tokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));

        if (tokenEntity.getUsedAt() != null) {
            throw new IllegalArgumentException("This reset link has already been used");
        }
        if (tokenEntity.getExpiresAt().before(new Date())) {
            throw new IllegalArgumentException("Invalid or expired reset link");
        }

        ValidationUtil.requireValidPassword(newPassword);

        UserEntity user = tokenEntity.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenEntity.setUsedAt(new Date());
        tokenRepository.save(tokenEntity);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JEP 249 / every JDK since 8) — unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
