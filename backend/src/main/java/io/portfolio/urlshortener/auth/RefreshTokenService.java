package io.portfolio.urlshortener.auth;

import io.portfolio.urlshortener.shortener.InfraUnavailableException;
import io.portfolio.urlshortener.shortener.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side refresh-session store. Each successful login/refresh mints a
 * new session id ({@code sid}) that is written to Redis at
 * {@code auth:refresh:{sid}} → user id, TTL = refresh remaining.
 *
 * <p>Local Native Fallback: If Redis is unavailable, falls back to an in-memory map
 * to support running natively without Docker dependencies.
 */
@Service
public class RefreshTokenService {

    static final String KEY_PREFIX = "auth:refresh:";
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Long> localFallback = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public RefreshTokenService(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    RefreshTokenService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public String newSessionId() {
        return UUID.randomUUID().toString();
    }

    static String snowflakeSid(SnowflakeIdGenerator gen) {
        return Long.toString(gen.nextId());
    }

    public void register(String sessionId, long userId, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + sessionId, Long.toString(userId), ttl);
        } catch (RuntimeException e) {
            log.warn("refresh-session register failed, using in-memory fallback: {}", e.toString());
            localFallback.put(sessionId, userId);
        }
    }

    public Long userIdFor(String sessionId) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + sessionId);
            return value == null ? localFallback.get(sessionId) : Long.parseLong(value);
        } catch (RuntimeException e) {
            log.warn("refresh-session read degraded, checking fallback: {}", e.toString());
            return localFallback.get(sessionId);
        }
    }

    public void revoke(String sessionId) {
        try {
            redis.delete(KEY_PREFIX + sessionId);
        } catch (RuntimeException e) {
            log.warn("refresh-session revoke failed: {}", e.toString());
        } finally {
            localFallback.remove(sessionId);
        }
    }

    public long secondsUntil(Instant expiresAt) {
        long s = Duration.between(clock.instant(), expiresAt).toSeconds();
        return Math.max(s, 0);
    }
}
