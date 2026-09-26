package com.secureportal.infra;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Fixed-window counter in Redis (INCR, and EXPIRE on the first hit), so every app instance shares one count. */
public class RedisRateLimiter implements RateLimiter {

    private static final String PREFIX = "rl:";

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Result hit(String key, int limit, Duration window) {
        String redisKey = PREFIX + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count == null) {
            return new Result(true, 0);
        }
        if (count == 1) {
            redis.expire(redisKey, window.toMillis(), TimeUnit.MILLISECONDS);
        }
        if (count <= limit) {
            return new Result(true, 0);
        }
        Long ttl = redis.getExpire(redisKey, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            // A counter without an expiry would block this key for good; give it one.
            redis.expire(redisKey, window.toMillis(), TimeUnit.MILLISECONDS);
            ttl = window.toSeconds();
        }
        return new Result(false, Math.max(1, ttl));
    }
}
