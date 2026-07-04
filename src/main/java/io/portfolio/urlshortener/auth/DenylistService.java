package io.portfolio.urlshortener.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Access-token JTI denylist (ADR-009). Fed by {@code POST /api/auth/logout};
 * consulted by {@link JwtAuthenticationFilter} on every authenticated
 * request.
 *
 * <p>Fail-OPEN on read: Redis down → "not denied" so the JWT signature
 * remains the source of truth for authentication. The stale access token
 * will still expire within its 15-minute TTL. Every fail-open decision is
 * counted so operators can alert on prolonged Redis unavailability.
 *
 * <p>Metric: {@code auth.denylist.checks} tagged
 * {@code outcome=hit|miss|store_unavailable}.
 *
 * <p>Gated on {@code app.jwt.secret} — same conditional as the rest of the
 * auth stack — so contexts without JWT don't build the denylist infra.
 */
@Service
@ConditionalOnProperty(prefix = "app.jwt", name = "secret")
public class DenylistService {

    static final String KEY_PREFIX = "auth:denylist:";
    static final String METRIC = "auth.denylist.checks";

    private static final Logger log = LoggerFactory.getLogger(DenylistService.class);

    private final StringRedisTemplate redis;
    private final Counter hits;
    private final Counter misses;
    private final Counter unavailable;

    public DenylistService(StringRedisTemplate redis, MeterRegistry meters) {
        this.redis = redis;
        this.hits = Counter.builder(METRIC).tag("outcome", "hit").register(meters);
        this.misses = Counter.builder(METRIC).tag("outcome", "miss").register(meters);
        this.unavailable = Counter.builder(METRIC).tag("outcome", "store_unavailable").register(meters);
    }

    public void deny(String jti, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (RuntimeException e) {
            // No-op — a missed denylist entry is bounded by the access-token TTL.
            log.warn("denylist add failed for jti {}: {}", jti, e.toString());
        }
    }

    public boolean isDenied(String jti) {
        try {
            Boolean present = redis.hasKey(KEY_PREFIX + jti);
            if (Boolean.TRUE.equals(present)) {
                hits.increment();
                return true;
            }
            misses.increment();
            return false;
        } catch (RuntimeException e) {
            unavailable.increment();
            log.warn("denylist check degraded to allow: {}", e.toString());
            return false;
        }
    }
}
