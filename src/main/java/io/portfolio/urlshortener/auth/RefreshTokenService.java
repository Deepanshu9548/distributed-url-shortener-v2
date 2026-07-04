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

/**
 * Server-side refresh-session store. Each successful login/refresh mints a
 * new session id ({@code sid}) that is written to Redis at
 * {@code auth:refresh:{sid}} → user id, TTL = refresh remaining. Rotation
 * (refresh) deletes the old sid and inserts a new one; logout deletes it.
 *
 * <p>Read paths ({@link #userIdFor(String)}) fail-OPEN when Redis is down —
 * the caller sees "session unknown" rather than a hard failure; the JWT
 * signature is still the primary authentication check. Write paths
 * ({@link #register}, {@link #revoke}) propagate infrastructure errors as
 * {@link InfraUnavailableException} → 503 upstream, so the client isn't
 * handed a token we can't remember.
 */
@Service
public class RefreshTokenService {

    static final String KEY_PREFIX = "auth:refresh:";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

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

    /** Snowflake generator (Track A) can also mint sids if callers prefer — unused here. */
    @SuppressWarnings("unused")
    static String snowflakeSid(SnowflakeIdGenerator gen) {
        return Long.toString(gen.nextId());
    }

    /**
     * @throws InfraUnavailableException when Redis write fails — the caller
     *         must NOT return the refresh token in that case.
     */
    public void register(String sessionId, long userId, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + sessionId, Long.toString(userId), ttl);
        } catch (RuntimeException e) {
            log.warn("refresh-session register failed: {}", e.toString());
            throw new InfraUnavailableException("refresh store unavailable");
        }
    }

    /**
     * Look up the user id for a session. Returns {@code null} when the
     * session is unknown OR when Redis is unreachable (fail-open on read).
     */
    public Long userIdFor(String sessionId) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + sessionId);
            return value == null ? null : Long.parseLong(value);
        } catch (RuntimeException e) {
            log.warn("refresh-session read degraded (fail-open): {}", e.toString());
            return null;
        }
    }

    /**
     * @throws InfraUnavailableException when Redis is unreachable — logout
     *         and refresh both need write success to avoid leaving live
     *         tokens behind.
     */
    public void revoke(String sessionId) {
        try {
            redis.delete(KEY_PREFIX + sessionId);
        } catch (RuntimeException e) {
            log.warn("refresh-session revoke failed: {}", e.toString());
            throw new InfraUnavailableException("refresh store unavailable");
        }
    }

    /** Convenience: seconds remaining until {@code expiresAt}, min 0. */
    public long secondsUntil(Instant expiresAt) {
        long s = Duration.between(clock.instant(), expiresAt).toSeconds();
        return Math.max(s, 0);
    }
}
