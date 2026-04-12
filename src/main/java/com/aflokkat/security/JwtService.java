package com.aflokkat.security;

import io.jsonwebtoken.Claims;

/**
 * Interface for JWT operations.
 * Extracted so consumers can be injected with a mock in tests
 * without requiring Byte Buddy class instrumentation (Java 25+).
 */
public interface JwtService {

    String generateAccessToken(String username, String role);

    String generateRefreshToken(String username);

    boolean validateToken(String token);

    Claims getClaimsIfValid(String token);

    String getUsernameFromToken(String token);

    String getRoleFromToken(String token);
}
