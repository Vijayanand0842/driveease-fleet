package com.driveease.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Shared HMAC-SHA256 JWT token issuer and verifier used across DriveEase services and API Gateway.
 */
public class JwtService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_FULL_NAME = "name";

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(String secret, long expirationMillis) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(Long userId, String email, String fullName, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claims(Map.of(
                        CLAIM_USER_ID, userId,
                        CLAIM_ROLE, role,
                        CLAIM_FULL_NAME, fullName
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public JwtUserPrincipal parsePrincipal(String token) throws JwtException {
        Claims claims = parseClaims(token);
        Number uidNum = claims.get(CLAIM_USER_ID, Number.class);
        Long userId = uidNum != null ? uidNum.longValue() : null;
        String role = claims.get(CLAIM_ROLE, String.class);
        String fullName = claims.get(CLAIM_FULL_NAME, String.class);
        return new JwtUserPrincipal(userId, claims.getSubject(), fullName, role);
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
