package org.example.infrastructure.web;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

public class TokenManager {

    private final SecretKey secretKey = Keys.hmacShaKeyFor("superSecretKey123456789012345678901234567890".getBytes());
    private final long expirationMillis = 3600_000; // 1 hora

    public String generateToken(Long userId, String email) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(secretKey)
                    .build().parseClaimsJws(token).getBody();
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            return null;
        }
    }
}
