package com.secureportal.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.Serializable;
import java.time.Duration;
import java.util.function.Supplier;

/** Values are stored with Java serialization, so cached types must be {@link Serializable}. */
public class RedisTtlCache implements TtlCache {

    private static final Logger log = LoggerFactory.getLogger(RedisTtlCache.class);
    private static final String PREFIX = "cache:";

    private final RedisTemplate<String, Object> redis;

    public RedisTtlCache(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T get(String key, Duration ttl, Supplier<T> loader) {
        if (ttl.isZero() || ttl.isNegative()) {
            return loader.get();
        }
        String redisKey = PREFIX + key;
        try {
            Object cached = redis.opsForValue().get(redisKey);
            if (cached != null) {
                return (T) cached;
            }
        } catch (RuntimeException e) {
            // A cache is an optimisation: if Redis hiccups, compute the value instead of failing the request.
            log.warn("Redis cache read failed for {}", key, e);
        }
        T value = loader.get();
        try {
            redis.opsForValue().set(redisKey, value, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis cache write failed for {}", key, e);
        }
        return value;
    }
}
