package com.skapt.app.security;

import com.skapt.app.config.AppConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public final class JwtUtil {
    private static final SecretKey KEY = Keys.hmacShaKeyFor(AppConfig.get("jwt.secret").getBytes(StandardCharsets.UTF_8));
    private static final long EXP_MS = Long.parseLong(AppConfig.get("jwt.expiration.ms"));

    private JwtUtil() {}

    public static String generateToken(long userId, String username, String role) {
        return Jwts.builder()
            .subject(username)
            .claim("userId", userId)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXP_MS))
            .signWith(KEY)
            .compact();
    }

    public static Claims parse(String token) {
        return Jwts.parser().verifyWith(KEY).build().parseSignedClaims(token).getPayload();
    }
}
