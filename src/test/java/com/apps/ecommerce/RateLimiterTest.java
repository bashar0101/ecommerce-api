package com.apps.ecommerce;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.apps.ecommerce.service.RateLimiter;

@ExtendWith(MockitoExtension.class)
public class RateLimiterTest {
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @InjectMocks
    private RateLimiter rateLimiter;

    @Test
    @DisplayName("expiry is set only on the first hit")
    void setsExpiryOnlyOnce() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("k")).thenReturn(1L, 2L, 3L);

        rateLimiter.allow("k", 5, 60);
        rateLimiter.allow("k", 5, 60);
        rateLimiter.allow("k", 5, 60);

        verify(redis, times(1)).expire(eq("k"), any(Duration.class));
    }

    @Test
    @DisplayName("blocks after the limit")
    void blocksOverLimit() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("k")).thenReturn(6L);

        assertFalse(rateLimiter.allow("k", 5, 60));
    }

    @Test
    @DisplayName("allows the request when Redis is down")
    void failsOpen() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertTrue(rateLimiter.allow("k", 5, 60));
    }
}
