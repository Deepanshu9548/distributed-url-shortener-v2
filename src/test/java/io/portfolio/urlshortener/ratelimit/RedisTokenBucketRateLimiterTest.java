package io.portfolio.urlshortener.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.portfolio.urlshortener.contracts.RateLimiter.RateLimitResult;
import io.portfolio.urlshortener.ratelimit.RateLimitProperties.LimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisTokenBucketRateLimiterTest {

    private static final long FIXED_MS = 1_700_000_000_000L;

    private StringRedisTemplate redis;
    private SimpleMeterRegistry meters;
    @SuppressWarnings("rawtypes") private RedisScript<List> script;
    private Clock clock;

    private RedisTokenBucketRateLimiter limiter(Map<String, LimiterConfig> map) {
        RateLimitProperties props = new RateLimitProperties(true, map);
        return new RedisTokenBucketRateLimiter(redis, props, meters, script, clock);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        meters = new SimpleMeterRegistry();
        script = mock(RedisScript.class);
        clock = Clock.fixed(Instant.ofEpochMilli(FIXED_MS), ZoneOffset.UTC);
    }

    @Test
    void allowed_returnsAllowedResultAndCountsAllowed() {
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(List.of(1L, 0L));

        RateLimitResult result = limiter(Map.of("write", new LimiterConfig(10, 60, false)))
                .check("rl:{user:1}:write", "write");

        assertThat(result.allowed()).isTrue();
        assertThat(result.retryAfterMillis()).isZero();
        assertThat(result.storeUnavailable()).isFalse();
        assertThat(counter("write", "allowed")).isEqualTo(1.0);
    }

    @Test
    void denied_carriesRetryAfterFromScriptResult() {
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(List.of(0L, 1500L));

        RateLimitResult result = limiter(Map.of("write", new LimiterConfig(10, 60, false)))
                .check("rl:{user:1}:write", "write");

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterMillis()).isEqualTo(1500);
        assertThat(result.storeUnavailable()).isFalse();
        assertThat(counter("write", "denied")).isEqualTo(1.0);
    }

    @Test
    void redisFailure_failClosedForWrite() {
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimitResult result = limiter(Map.of("write", new LimiterConfig(10, 60, false)))
                .check("rl:{user:1}:write", "write");

        assertThat(result.allowed()).isFalse();
        assertThat(result.storeUnavailable()).isTrue();
        assertThat(counter("write", "fail_closed")).isEqualTo(1.0);
    }

    @Test
    void redisFailure_failOpenForRedirect() {
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimitResult result = limiter(Map.of("redirect", new LimiterConfig(100, 100, true)))
                .check("rl:{ip:1.2.3.4}:redirect", "redirect");

        assertThat(result.allowed()).isTrue();
        assertThat(result.storeUnavailable()).isTrue();
        assertThat(counter("redirect", "fail_open")).isEqualTo(1.0);
    }

    @Test
    void unknownLimiter_failsClosedAndCounts() {
        RateLimitResult result = limiter(Map.of()).check("rl:{ip:1.2.3.4}:mystery", "mystery");

        assertThat(result.allowed()).isFalse();
        assertThat(result.storeUnavailable()).isTrue();
        assertThat(counter("mystery", "fail_closed")).isEqualTo(1.0);
    }

    @Test
    void unexpectedScriptResult_appliesFailurePolicy() {
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(null);

        RateLimitResult failClosed = limiter(Map.of("write", new LimiterConfig(10, 60, false)))
                .check("rl:{u}:write", "write");
        assertThat(failClosed.allowed()).isFalse();
        assertThat(failClosed.storeUnavailable()).isTrue();

        RateLimitResult failOpen = limiter(Map.of("redirect", new LimiterConfig(100, 100, true)))
                .check("rl:{u}:redirect", "redirect");
        assertThat(failOpen.allowed()).isTrue();
        assertThat(failOpen.storeUnavailable()).isTrue();
    }

    @Test
    void executeArgs_carryCapacityRefillNowAndRequested() {
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(List.of(1L, 0L));

        limiter(Map.of("write", new LimiterConfig(10, 60, false)))
                .check("rl:{user:1}:write", "write");

        String expectedRefill = String.valueOf(60.0 / 60_000.0);
        verify(redis).execute(
                eq(script),
                eq(List.of("rl:{user:1}:write")),
                eq("10"),
                eq(expectedRefill),
                eq(String.valueOf(FIXED_MS)),
                eq("1"));
    }

    @Test
    void integerResults_alsoParseCorrectly() {
        // Some codecs surface Lua integers as java.lang.Long, some as Integer/String.
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(List.of("0", "800"));

        RateLimitResult result = limiter(Map.of("write", new LimiterConfig(10, 60, false)))
                .check("rl:{u}:write", "write");

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterMillis()).isEqualTo(800);
    }

    private double counter(String limiterName, String outcome) {
        return meters.counter("ratelimit.decisions", "limiter", limiterName, "outcome", outcome).count();
    }
}
