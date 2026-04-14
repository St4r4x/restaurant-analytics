package com.aflokkat.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
    }

    // ── generateAccessToken ───────────────────────────────────────────────────

    @Test
    void generateAccessToken_returnsNonNullToken() {
        assertNotNull(jwtUtil.generateAccessToken("alice", "ROLE_USER"));
    }

    @Test
    void generateAccessToken_tokenIsValid() {
        String token = jwtUtil.generateAccessToken("alice", "ROLE_USER");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void generateAccessToken_containsUsername() {
        String token = jwtUtil.generateAccessToken("alice", "ROLE_USER");
        assertEquals("alice", jwtUtil.getUsernameFromToken(token));
    }

    @Test
    void generateAccessToken_containsRole() {
        String token = jwtUtil.generateAccessToken("alice", "ROLE_ADMIN");
        assertEquals("ROLE_ADMIN", jwtUtil.getRoleFromToken(token));
    }

    // ── generateRefreshToken ──────────────────────────────────────────────────

    @Test
    void generateRefreshToken_returnsValidToken() {
        String token = jwtUtil.generateRefreshToken("bob");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void generateRefreshToken_containsUsername() {
        String token = jwtUtil.generateRefreshToken("bob");
        assertEquals("bob", jwtUtil.getUsernameFromToken(token));
    }

    // ── validateToken ─────────────────────────────────────────────────────────

    @Test
    void validateToken_returnsFalse_forGarbageString() {
        assertFalse(jwtUtil.validateToken("not.a.jwt"));
    }

    @Test
    void validateToken_returnsFalse_forEmptyString() {
        assertFalse(jwtUtil.validateToken(""));
    }

    @Test
    void validateToken_returnsFalse_forTamperedToken() {
        String token = jwtUtil.generateAccessToken("alice", "ROLE_USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtUtil.validateToken(tampered));
    }

    // ── getClaimsIfValid ──────────────────────────────────────────────────────

    @Test
    void getClaimsIfValid_returnsClaims_forValidToken() {
        String token = jwtUtil.generateAccessToken("carol", "ROLE_USER");
        assertNotNull(jwtUtil.getClaimsIfValid(token));
    }

    @Test
    void getClaimsIfValid_returnsNull_forInvalidToken() {
        assertNull(jwtUtil.getClaimsIfValid("invalid.token.here"));
    }

    // ── access vs refresh token independence ─────────────────────────────────

    @Test
    void accessAndRefreshTokensAreDifferent() {
        String access  = jwtUtil.generateAccessToken("dave", "ROLE_USER");
        String refresh = jwtUtil.generateRefreshToken("dave");
        assertNotEquals(access, refresh);
    }
}
