package com.st4r4x.security;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        // Inject jwt.secret into AppConfig.properties static field before JwtUtil construction.
        // AppConfig.getProperty() checks System.getenv() first — System.setProperty() does NOT
        // work here. Direct reflection patch is the established project pattern (STATE.md).
        Field f = com.st4r4x.config.AppConfig.class.getDeclaredField("properties");
        f.setAccessible(true);
        Properties props = (Properties) f.get(null);
        props.setProperty("jwt.secret",
            "test-only-jwt-secret-64chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        jwtUtil = new JwtUtil();
    }

    @AfterEach
    void tearDown() throws Exception {
        Field f = com.st4r4x.config.AppConfig.class.getDeclaredField("properties");
        f.setAccessible(true);
        Properties props = (Properties) f.get(null);
        props.remove("jwt.secret");
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
