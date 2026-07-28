package com.st4r4x.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.st4r4x.config.AppConfig;

@Service
public class ResendEmailService implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String FROM_ADDRESS = "onboarding@resend.dev";

    private final Resend resend;

    public ResendEmailService() {
        this.resend = new Resend(AppConfig.getResendApiKey());
    }

    // Constructor for test injection
    ResendEmailService(Resend resend) {
        this.resend = resend;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        CreateEmailOptions params = CreateEmailOptions.builder()
            .from(FROM_ADDRESS)
            .to(toEmail)
            .subject("Reset your password — NYC Restaurant Inspector")
            .html("<p>We received a request to reset your password.</p>"
                + "<p><a href=\"" + resetLink + "\">Click here to reset your password</a></p>"
                + "<p>This link expires in 1 hour. If you didn't request this, you can ignore this email.</p>")
            .build();

        try {
            resend.emails().send(params);
        } catch (ResendException e) {
            logger.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
}
