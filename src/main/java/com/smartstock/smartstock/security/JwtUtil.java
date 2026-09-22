package com.smartstock.smartstock.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    // Secret key from application.yml
    // Must be at least 256 bits for HS256
    @Value("${jwt.secret}")
    private String jwtSecret;

    // Token expiry — 24 hours in milliseconds
    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    // Generate signing key from secret string
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // Generate JWT token for a user
    public String generateToken(String email, String role,
                                Long userId, Long warehouseId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        claims.put("warehouseId", warehouseId);

        return Jwts.builder()
                .setClaims(claims)         // payload data
                .setSubject(email)         // who this token is for
                .setIssuedAt(new Date())   // when created
                .setExpiration(new Date(   // when it expires
                        System.currentTimeMillis() + jwtExpiration
                ))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Extract email from token
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    // Extract role from token
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    // Extract userId from token
    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    // Extract warehouseId from token
    public Long extractWarehouseId(String token) {
        return extractAllClaims(token).get("warehouseId", Long.class);
    }

    // Validate token — check signature and expiry
    public boolean validateToken(String token, String email) {
        try {
            String extractedEmail = extractEmail(token);
            return extractedEmail.equals(email) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Check if token has expired
    private boolean isTokenExpired(String token) {
        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());
    }

    // Parse and return all claims from token
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}