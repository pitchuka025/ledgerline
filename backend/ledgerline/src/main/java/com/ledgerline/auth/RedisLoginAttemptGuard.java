package com.ledgerline.auth;

import com.ledgerline.common.ApiExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "ledgerline.security.login-rate-limit", name = "enabled", havingValue = "true")
public class RedisLoginAttemptGuard implements LoginAttemptGuard {
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>("""
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return attempts
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final long windowSeconds;

    public RedisLoginAttemptGuard(StringRedisTemplate redis,
                                  @Value("${ledgerline.security.login-rate-limit.max-attempts:5}") int maxAttempts,
                                  @Value("${ledgerline.security.login-rate-limit.window:PT15M}") Duration window,
                                  @Value("${spring.data.redis.password:}") String redisPassword) {
        if (redisPassword.isBlank()) {
            throw new IllegalStateException("REDIS_PASSWORD must be set when Redis login rate limiting is enabled");
        }
        if (maxAttempts < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("Redis login rate-limit attempts and window must be positive");
        }
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.windowSeconds = Math.max(1, window.toSeconds());
    }

    @Override
    public void checkAllowed(String email, String clientAddress) {
        String key = key(email, clientAddress);
        String failures = redis.opsForValue().get(key);
        if (failures != null && Long.parseLong(failures) >= maxAttempts) {
            long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            throw new ApiExceptions.TooManyRequests(Math.max(1, ttl));
        }
    }

    @Override
    public void recordFailure(String email, String clientAddress) {
        Long attempts = redis.execute(INCREMENT_WITH_EXPIRY, List.of(key(email, clientAddress)),
                Long.toString(windowSeconds));
        if (attempts == null) {
            throw new IllegalStateException("Redis did not return the login-attempt count");
        }
    }

    @Override
    public void clear(String email, String clientAddress) {
        redis.delete(key(email, clientAddress));
    }

    private String key(String email, String clientAddress) {
        String value = email.trim().toLowerCase(Locale.ROOT) + "\u0000"
                + (clientAddress == null ? "" : clientAddress.trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "ledgerline:auth:login-fail:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
