package io.portfolio.urlshortener.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import io.portfolio.urlshortener.contracts.RateLimiter;
import io.portfolio.urlshortener.ratelimit.RateLimitProperties.LimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Real {@link RateLimiter} (ADR-005). Executes the atomic token-bucket Lua
 * script ({@code lua/token_bucket.lua}) via {@link StringRedisTemplate} —
 * spring-data-redis handles EVALSHA + EVAL fallback.
 *
 * <p><b>Bean availability:</b> {@code @ConditionalOnProperty} keeps this bean
 * out of the context when {@code ratelimit.enabled=false} (load-test toggle)
 * — the {@code AllowAllRateLimiter} stub then becomes the only
 * {@link RateLimiter} bean. When rate limiting is on, {@code @Primary} makes
 * this bean win injection over the stub without touching contracts territory
 * (same supersede pattern as {@code RedisUrlCache} / {@code HashRingShardRouter}).
 *
 * <p><b>Failure policy:</b> any {@link RuntimeException} from Redis follows
 * the per-limiter {@code failOpen} config — writes/auth fail-CLOSED (503
 * upstream), redirects fail-OPEN (availability wins).
 *
 * <p>Metric: {@code ratelimit.decisions} tagged
 * {@code limiter=<name>, outcome=allowed|denied|fail_open|fail_closed}
 * (allowed labels only — NEVER the subject/key, which would blow up cardinality).
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisTokenBucketRateLimiter implements RateLimiter {

    static final String DECISION_METRIC = "ratelimit.decisions";

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final MeterRegistry meterRegistry;
    @SuppressWarnings("rawtypes") // spring-data-redis' RedisScript<T>.setResultType takes a Class, so we can't parameterize List's element type.
    private final RedisScript<List> script;
    private final Clock clock;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis,
                                      RateLimitProperties properties,
                                      MeterRegistry meterRegistry) {
        this(redis, properties, meterRegistry, loadScript(), Clock.systemUTC());
    }

    /** Package-private for tests: inject a fixed Clock and/or a mocked script. */
    @SuppressWarnings("rawtypes")
    RedisTokenBucketRateLimiter(StringRedisTemplate redis,
                                RateLimitProperties properties,
                                MeterRegistry meterRegistry,
                                RedisScript<List> script,
                                Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.script = script;
        this.clock = clock;
    }

    @SuppressWarnings("rawtypes")
    private static RedisScript<List> loadScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/token_bucket.lua")));
        script.setResultType(List.class);
        return script;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult check(String key, String limiterName) {
        LimiterConfig config = properties.forName(limiterName).orElse(null);
        if (config == null) {
            log.warn("unknown rate limiter '{}' — failing closed", limiterName);
            count(limiterName, "fail_closed");
            return RateLimitResult.failClosed();
        }

        try {
            List<Object> result = redis.execute(
                    script,
                    List.of(key),
                    String.valueOf(config.capacity()),
                    String.valueOf(config.refillPerMillisecond()),
                    String.valueOf(clock.millis()),
                    "1");

            if (result == null || result.size() < 2) {
                // Defensive: unexpected script response shape — treat as store unavailable.
                log.warn("unexpected token_bucket script result for limiter '{}': {}", limiterName, result);
                return applyFailurePolicy(limiterName, config);
            }

            long allowed = toLong(result.get(0));
            long retryAfterMs = toLong(result.get(1));

            if (allowed == 1L) {
                count(limiterName, "allowed");
                return RateLimitResult.allowedResult();
            }
            count(limiterName, "denied");
            return RateLimitResult.denied(retryAfterMs);
        } catch (RuntimeException e) {
            log.warn("redis rate limiter '{}' degraded ({}): {}",
                    limiterName, config.failOpen() ? "fail-open" : "fail-closed", e.toString());
            return applyFailurePolicy(limiterName, config);
        }
    }

    private RateLimitResult applyFailurePolicy(String limiterName, LimiterConfig config) {
        if (config.failOpen()) {
            count(limiterName, "fail_open");
            return RateLimitResult.failOpen();
        }
        count(limiterName, "fail_closed");
        return RateLimitResult.failClosed();
    }

    private void count(String limiter, String outcome) {
        meterRegistry.counter(DECISION_METRIC, "limiter", limiter, "outcome", outcome).increment();
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        // Redis EVAL results occasionally arrive as strings depending on codec paths.
        return Long.parseLong(String.valueOf(value));
    }
}
