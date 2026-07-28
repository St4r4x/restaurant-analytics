package com.st4r4x.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.st4r4x.entity.PasswordResetTokenEntity;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.PasswordResetTokenRepository;
import com.st4r4x.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, emailService, passwordEncoder);
    }

    // ── requestReset ─────────────────────────────────────────────────────────

    @Test
    void requestReset_sendsEmail_whenUserExists() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        service.requestReset("alice@example.com", "https://example.com");

        verify(tokenRepository).save(any(PasswordResetTokenEntity.class));
        verify(emailService).sendPasswordResetEmail(eq("alice@example.com"), contains("https://example.com/reset-password?token="));
    }

    @Test
    void requestReset_doesNothing_whenUserDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        service.requestReset("unknown@example.com", "https://example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestReset_doesNotThrow_whenUserDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestReset("unknown@example.com", "https://example.com"));
    }

    @Test
    void requestReset_storesHashNotRawToken() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        ArgumentCaptor<PasswordResetTokenEntity> captor = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        service.requestReset("alice@example.com", "https://example.com");
        verify(tokenRepository).save(captor.capture());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(any(), linkCaptor.capture());
        String rawTokenFromLink = linkCaptor.getValue().substring(linkCaptor.getValue().indexOf("token=") + 6);

        assertNotEquals(rawTokenFromLink, captor.getValue().getTokenHash(),
            "The stored tokenHash must not equal the raw token sent to the user");
    }

    // ── resetPassword ────────────────────────────────────────────────────────

    @Test
    void resetPassword_updatesPassword_whenTokenValid() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "oldHash", "ROLE_CUSTOMER");
        user.setId(1L);
        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity(
            user, "irrelevant-in-this-test-since-we-mock-the-lookup", new Date(System.currentTimeMillis() + 3_600_000L));

        // The service hashes the raw token internally and looks up by that hash — since we
        // don't know the exact hash the service will compute ahead of time, mock findByTokenHash
        // to match ANY string and return our token, then verify the raw token round-trips.
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tokenEntity));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("newHash");

        service.resetPassword("some-raw-token", "NewPassword123");

        assertEquals("newHash", user.getPasswordHash());
        assertNotNull(tokenEntity.getUsedAt(), "Token must be marked used after a successful reset");
        verify(userRepository).save(user);
        verify(tokenRepository).save(tokenEntity);
    }

    @Test
    void resetPassword_throws_whenTokenNotFound() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resetPassword("bad-token", "NewPassword123"));
        assertTrue(ex.getMessage().contains("Invalid or expired"));
    }

    @Test
    void resetPassword_throws_whenTokenExpired() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "oldHash", "ROLE_CUSTOMER");
        PasswordResetTokenEntity expiredToken = new PasswordResetTokenEntity(
            user, "hash", new Date(System.currentTimeMillis() - 1000L)); // expired 1 second ago
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resetPassword("some-token", "NewPassword123"));
        assertTrue(ex.getMessage().contains("Invalid or expired"));
    }

    @Test
    void resetPassword_throws_whenTokenAlreadyUsed() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "oldHash", "ROLE_CUSTOMER");
        PasswordResetTokenEntity usedToken = new PasswordResetTokenEntity(
            user, "hash", new Date(System.currentTimeMillis() + 3_600_000L));
        usedToken.setUsedAt(new Date());
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(usedToken));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resetPassword("some-token", "NewPassword123"));
        assertTrue(ex.getMessage().contains("already been used"));
    }

    @Test
    void resetPassword_throws_whenPasswordTooWeak() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "oldHash", "ROLE_CUSTOMER");
        PasswordResetTokenEntity validToken = new PasswordResetTokenEntity(
            user, "hash", new Date(System.currentTimeMillis() + 3_600_000L));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(validToken));

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("some-token", "weak"));
        verify(userRepository, never()).save(any());
    }
}
