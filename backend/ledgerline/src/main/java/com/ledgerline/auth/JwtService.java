package com.ledgerline.auth;

import com.ledgerline.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${ledgerline.jwt.secret}") String secret,
                      @Value("${ledgerline.jwt.expiration}") Duration expiration) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("LEDGER_JWT_SECRET must contain at least 32 bytes");
        }
        if (expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException("LEDGER_JWT_EXPIRATION must be positive");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public UUID userIdFrom(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
