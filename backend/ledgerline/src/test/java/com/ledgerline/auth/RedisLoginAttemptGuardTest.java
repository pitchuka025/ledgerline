package com.ledgerline.auth;

import com.ledgerline.common.ApiExceptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLoginAttemptGuardTest {
    @Test
    void blocksAttemptsBeyondTheConfiguredLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        when(redis.opsForValue().get(anyString())).thenReturn("5");
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(120L);
        RedisLoginAttemptGuard guard = new RedisLoginAttemptGuard(redis, 5, Duration.ofMinutes(15), "secret");

        assertThatThrownBy(() -> guard.checkAllowed("customer@example.test", "127.0.0.1"))
                .isInstanceOf(ApiExceptions.TooManyRequests.class)
                .hasMessageContaining("failed sign-in attempts");
    }

    @Test
    void incrementsOnlyWhenRecordingAFailedAttempt() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), ArgumentMatchers.<List<String>>any(),
                any(Object[].class))).thenReturn(1L);
        RedisLoginAttemptGuard guard = new RedisLoginAttemptGuard(redis, 5, Duration.ofMinutes(15), "secret");

        guard.recordFailure("customer@example.test", "127.0.0.1");

        verify(redis).execute(ArgumentMatchers.<RedisScript<Long>>any(), ArgumentMatchers.<List<String>>any(),
                any(Object[].class));
    }

    @Test
    void requiresRedisPasswordWhenFeatureIsEnabled() {
        assertThatThrownBy(() -> new RedisLoginAttemptGuard(mock(StringRedisTemplate.class), 5,
                Duration.ofMinutes(15), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_PASSWORD");
    }

    @Test
    void clearsTheHashedAttemptKeyAfterSuccessfulLogin() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisLoginAttemptGuard guard = new RedisLoginAttemptGuard(redis, 5, Duration.ofMinutes(15), "secret");

        guard.clear("customer@example.test", "127.0.0.1");

        verify(redis).delete(anyString());
    }
}
