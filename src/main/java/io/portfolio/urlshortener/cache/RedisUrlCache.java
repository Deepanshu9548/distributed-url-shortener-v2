package io.portfolio.urlshortener.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.portfolio.urlshortener.contracts.UrlCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed {@link UrlCache} (ADR-004). Keys: {@code url:{code}} for
 * mappings, {@code lock:url:{code}} for the stampede lock
 * ({@code SET NX PX 2000}). Negative sentinel {@code __NOT_FOUND__} @ 60s.
 *
 * <p><b>Degrade, never fail</b> (contract): every Redis {@link RuntimeException}
 * is swallowed — reads become {@code Miss}, writes become no-ops,
 * {@code tryLock} grants (Redis down = no stampede gate, callers fall through
 * to the DB exactly like the NoopCache stub). Each swallow increments
 * {@code cache.errors{source=redis}}.
 *
 * <p><b>Known limitation:</b> {@code unlock} is a plain best-effort DELETE with
 * no fencing token — a holder that outlives the 2s lock TTL can delete a lock
 * that has since been re-granted to another caller. Acceptable here: the lock
 * only bounds duplicate DB loads, it guards no invariant.
 *
 * <p>{@code @Primary} so this bean wins over the M0 {@code NoopCache} stub
 * without touching contracts territory.
 */
@Component
@Primary
public class RedisUrlCache implements UrlCache {

    static final String NEGATIVE_SENTINEL = "__NOT_FOUND__";
    static final Duration NEGATIVE_TTL = Duration.ofSeconds(60);
    static final Duration LOCK_TTL = Duration.ofMillis(2000);
    static final String KEY_PREFIX = "url:";
    static final String LOCK_PREFIX = "lock:url:";

    private static final Logger log = LoggerFactory.getLogger(RedisUrlCache.class);

    private final StringRedisTemplate redis;
    private final Counter hits;
    private final Counter misses;
    private final Counter negativeHits;
    private final Counter errors;

    public RedisUrlCache(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        // Metric names per CONVENTIONS.md: lowercase dot-separated, no short-code labels.
        this.hits = Counter.builder("cache.hits").tag("source", "redis").register(meterRegistry);
        this.misses = Counter.builder("cache.misses").tag("source", "redis").register(meterRegistry);
        this.negativeHits = Counter.builder("cache.negative.hits").tag("source", "redis").register(meterRegistry);
        this.errors = Counter.builder("cache.errors").tag("source", "redis").register(meterRegistry);
    }

    @Override
    public CacheResult get(String shortCode) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + shortCode);
            if (value == null) {
                misses.increment();
                return new Miss();
            }
            if (NEGATIVE_SENTINEL.equals(value)) {
                negativeHits.increment();
                return new NegativeHit();
            }
            hits.increment();
            return new Hit(value);
        } catch (RuntimeException e) {
            swallow("get", e);
            return new Miss();
        }
    }

    @Override
    public void put(String shortCode, String longUrl, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + shortCode, longUrl, ttl);
        } catch (RuntimeException e) {
            swallow("put", e);
        }
    }

    @Override
    public void putNegative(String shortCode) {
        try {
            redis.opsForValue().set(KEY_PREFIX + shortCode, NEGATIVE_SENTINEL, NEGATIVE_TTL);
        } catch (RuntimeException e) {
            swallow("putNegative", e);
        }
    }

    @Override
    public void evict(String shortCode) {
        try {
            redis.delete(KEY_PREFIX + shortCode);
        } catch (RuntimeException e) {
            swallow("evict", e);
        }
    }

    @Override
    public boolean tryLock(String shortCode) {
        try {
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(LOCK_PREFIX + shortCode, "1", LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException e) {
            swallow("tryLock", e);
            // Redis down → no stampede gate; grant so the caller proceeds to the DB
            // (same degradation the NoopCache stub certifies).
            return true;
        }
    }

    @Override
    public void unlock(String shortCode) {
        try {
            redis.delete(LOCK_PREFIX + shortCode);
        } catch (RuntimeException e) {
            swallow("unlock", e);
        }
    }

    private void swallow(String operation, RuntimeException e) {
        errors.increment();
        log.warn("redis cache {} degraded to miss/no-op: {}", operation, e.toString());
    }
}
