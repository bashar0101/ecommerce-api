package com.apps.ecommerce.service;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

    private final StringRedisTemplate redis;

    /** Returns true if the action is allowed. */
    public boolean allow(String key, int max, int windowSeconds) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count == null || count <= max;
        } catch (DataAccessException ex) {
            // DataAccessException, not RedisConnectionFailureException: a refused
            // connection throws the latter, but a host that accepts and then hangs
            // trips the command timeout and throws QueryTimeoutException instead.
            // Both extend DataAccessException, which is what Spring translates every
            // Redis failure into — catching the subclass let real outages through.
            log.warn("Redis unavailable, rate limiting skipped", ex);
            return true; // let the request through
        }
    }
}
