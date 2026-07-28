package com.st4r4x.service;

/**
 * Abstraction over the email-sending provider. Exists so PasswordResetService
 * can be tested without a real network call — mirrors the JwtService/JwtUtil
 * interface-plus-implementation pattern already used in this codebase.
 */
public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
