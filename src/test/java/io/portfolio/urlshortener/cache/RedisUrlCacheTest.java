package io.portfolio.urlshortener.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.portfolio.urlshortener.contracts.UrlCache.Hit;
import io.portfolio.urlshortener.contracts.UrlCache.Miss;
import io.portfolio.urlshortener.contracts.UrlCache.NegativeHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Logic + degradation tests against a mocked StringRedisTemplate. Storage
 * semantics against real Redis are certified by {@code RedisUrlCacheIT}
 * (docker-tagged contract test).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisUrlCacheTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private SimpleMeterRegistry meters;
    private RedisUrlCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        meters = new SimpleMeterRegistry();
        cache = new RedisUrlCache(redis, meters);
    }

    private double counter(String name) {
        return meters.counter(name, "source", "redis").count();
    }

    // --- happy paths ---

    @Test
    void getReturnsHitAndCountsIt() {
        when(valueOps.get("url:abc")).thenReturn("https://example.com");
        assertThat(cache.get("abc")).isEqualTo(new Hit("https://example.com"));
        assertThat(counter("cache.hits")).isEqualTo(1.0);
    }

    @Test
    void getReturnsMissForAbsentKeyAndCountsIt() {
        when(valueOps.get("url:abc")).thenReturn(null);
        assertThat(cache.get("abc")).isInstanceOf(Miss.class);
        assertThat(counter("cache.misses")).isEqualTo(1.0);
    }

    @Test
    void sentinelValueIsNegativeHitNotHit() {
        when(valueOps.get("url:abc")).thenReturn("__NOT_FOUND__");
        assertThat(cache.get("abc")).isInstanceOf(NegativeHit.class);
        assertThat(counter("cache.negative.hits")).isEqualTo(1.0);
        assertThat(counter("cache.hits")).isZero();
    }

    @Test
    void putWritesWithGivenTtl() {
        cache.put("abc", "https://example.com", Duration.ofMinutes(10));
        verify(valueOps).set("url:abc", "https://example.com", Duration.ofMinutes(10));
    }

    @Test
    void putNegativeWritesSentinelWith60sTtl() {
        cache.putNegative("abc");
        verify(valueOps).set("url:abc", "__NOT_FOUND__", Duration.ofSeconds(60));
    }

    @Test
    void evictDeletesUrlKey() {
        cache.evict("abc");
        verify(redis).delete("url:abc");
    }

    @Test
    void tryLockUsesSetNxWith2sTtlAndUnlockDeletes() {
        when(valueOps.setIfAbsent("lock:url:abc", "1", Duration.ofMillis(2000))).thenReturn(true);
        assertThat(cache.tryLock("abc")).isTrue();

        when(valueOps.setIfAbsent("lock:url:abc", "1", Duration.ofMillis(2000))).thenReturn(false);
        assertThat(cache.tryLock("abc")).isFalse();

        cache.unlock("abc");
        verify(redis).delete("lock:url:abc");
    }

    // --- degradation: every Redis error → Miss/no-op + cache.errors ---

    @Test
    void getDegradesToMissWhenRedisThrows() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(cache.get("abc")).isInstanceOf(Miss.class);
        assertThat(counter("cache.errors")).isEqualTo(1.0);
    }

    @Test
    void putIsSilentNoopWhenRedisThrows() {
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> cache.put("abc", "https://example.com", Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> cache.putNegative("abc")).doesNotThrowAnyException();
        assertThat(counter("cache.errors")).isEqualTo(2.0);
    }

    @Test
    void evictIsSilentNoopWhenRedisThrows() {
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> cache.evict("abc")).doesNotThrowAnyException();
        assertThat(counter("cache.errors")).isEqualTo(1.0);
    }

    @Test
    void tryLockGrantsWhenRedisThrows() {
        // Redis down = no stampede gate; caller must be allowed through to the DB
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));
        assertThat(cache.tryLock("abc")).isTrue();
        assertThat(counter("cache.errors")).isEqualTo(1.0);
    }

    @Test
    void tryLockIsFalseWhenRedisReturnsNull() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);
        assertThat(cache.tryLock("abc")).isFalse();
    }

    @Test
    void unlockIsSilentNoopWhenRedisThrows() {
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> cache.unlock("abc")).doesNotThrowAnyException();
        assertThat(counter("cache.errors")).isEqualTo(1.0);
    }
}
