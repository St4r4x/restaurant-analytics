package com.aflokkat.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import com.aflokkat.config.AppConfig;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class JwtUtil implements JwtService {
    private final Key key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtUtil() {
        this.key = Keys.hmacShaKeyFor(AppConfig.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = AppConfig.getJwtAccessTokenExpirationMs();
        this.refreshTokenValidityMs = AppConfig.getJwtRefreshTokenExpirationMs();
    }

    public String generateAccessToken(String username, String role) {
        return buildToken(username, role, accessTokenValidityMs);
    }

    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenValidityMs);
        return Jwts.builder()
            .setSubject(username)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }

    private String buildToken(String username, String role, long ttl) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttl);

        return Jwts.builder()
            .setSubject(username)
            .claim("role", role)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Returns the Claims if the token is valid, or null if invalid/expired. */
    public Claims getClaimsIfValid(String token) {
        try {
            return parseClaims(token);
        } catch (Exception ex) {
            return null;
        }
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }
}