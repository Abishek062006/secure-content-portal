package com.secureportal.infra;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfraTest {

    @Test
    void theInMemoryLimiterAllowsUpToTheLimitThenBlocksWithARetryTimeAndKeysAreIndependent() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.hit("a", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        RateLimiter.Result blocked = limiter.hit("a", 3, Duration.ofMinutes(1));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(limiter.hit("b", 3, Duration.ofMinutes(1)).allowed()).as("another key has its own budget").isTrue();
    }

    @Test
    void theInMemoryLimiterStartsAFreshWindowOnceTheOldOneEnds() throws Exception {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        assertThat(limiter.hit("k", 1, Duration.ofMillis(400)).allowed()).isTrue();
        assertThat(limiter.hit("k", 1, Duration.ofMillis(400)).allowed()).isFalse();
        Thread.sleep(600);
        assertThat(limiter.hit("k", 1, Duration.ofMillis(400)).allowed()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRedisLimiterCountsWithIncrAndExpiresTheKeyOnTheFirstHit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment("rl:x")).thenReturn(1L, 2L, 3L);
        when(redis.getExpire("rl:x", TimeUnit.SECONDS)).thenReturn(42L);
        RedisRateLimiter limiter = new RedisRateLimiter(redis);

        assertThat(limiter.hit("x", 2, Duration.ofMinutes(1)).allowed()).isTrue();
        verify(redis).expire("rl:x", 60_000L, TimeUnit.MILLISECONDS);
        assertThat(limiter.hit("x", 2, Duration.ofMinutes(1)).allowed()).isTrue();
        RateLimiter.Result third = limiter.hit("x", 2, Duration.ofMinutes(1));
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isEqualTo(42);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRedisCounterThatLostItsExpiryGetsOneSoItCannotBlockForever() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(9L);
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-1L);

        RateLimiter.Result result = new RedisRateLimiter(redis).hit("y", 2, Duration.ofSeconds(30));

        assertThat(result.allowed()).isFalse();
        verify(redis).expire("rl:y", 30_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void theInMemoryCacheHoldsAValueUntilItExpiresAndZeroBypassesIt() throws Exception {
        InMemoryTtlCache cache = new InMemoryTtlCache();
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("k", Duration.ofMillis(400), () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
        assertThat(cache.get("k", Duration.ofMillis(400), () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
        Thread.sleep(600);
        assertThat(cache.get("k", Duration.ofMillis(400), () -> "v" + loads.incrementAndGet())).isEqualTo("v2");

        assertThat(cache.get("z", Duration.ZERO, () -> "a")).isEqualTo("a");
        assertThat(cache.get("z", Duration.ZERO, () -> "b")).as("a zero TTL never caches").isEqualTo("b");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRedisCacheReadsThroughAndFallsBackToTheLoaderWhenRedisFails() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        RedisTtlCache cache = new RedisTtlCache(redis);

        when(ops.get("cache:hit")).thenReturn("cached");
        assertThat(cache.get("hit", Duration.ofSeconds(30), () -> "fresh")).isEqualTo("cached");
        verify(ops, never()).set(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Duration.class));

        when(ops.get("cache:miss")).thenReturn(null);
        assertThat(cache.get("miss", Duration.ofSeconds(30), () -> "fresh")).isEqualTo("fresh");
        verify(ops).set("cache:miss", "fresh", Duration.ofSeconds(30));

        when(ops.get("cache:down")).thenThrow(new IllegalStateException("redis is down"));
        assertThat(cache.get("down", Duration.ofSeconds(30), () -> "fallback")).isEqualTo("fallback");
    }
}
