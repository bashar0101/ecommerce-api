package com.apps.ecommerce.config;

import java.time.Duration;

import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()));
    }

    /**
     * Spring's default handler rethrows, so an unreachable Redis turns every
     * @Cacheable read into a 500 — a cache outage taking down the endpoint the
     * cache was meant to speed up. Swallowing instead makes a miss look like any
     * other miss, and the method runs against the database as normal.
     *
     * Same fail-open reasoning as RateLimiter: these are optimisations and
     * protections, not sources of truth. Logged at warn so it cannot pass unseen.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache unavailable on read [{}::{}], falling through to the database", cache.getName(), key,
                        ex);
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache unavailable on write [{}::{}], value not cached", cache.getName(), key, ex);
            }

            /**
             * The risky one: a failed evict leaves a stale entry behind. Harmless
             * while Redis is down (reads fail through to the database anyway), but
             * once it returns, that entry serves outdated data until its 10 minute
             * TTL expires. The TTL is what bounds the damage.
             */
            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache unavailable on evict [{}::{}], entry may be stale until its TTL expires",
                        cache.getName(), key, ex);
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache unavailable on clear [{}]", cache.getName(), ex);
            }
        };
    }
}
