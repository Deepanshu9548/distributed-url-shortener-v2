package io.portfolio.urlshortener.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;
import java.util.Optional;

/**
 * Binds the {@code ratelimit} section of application.yml. Discovered via
 * {@code @ConfigurationPropertiesScan} on the main app (constructor binding —
 * plain records, no Lombok).
 *
 * <p>{@code enabled=false} (load-test toggle) drops the
 * {@code RedisTokenBucketRateLimiter} and {@code RateLimitFilter} from the
 * context — the {@code AllowAllRateLimiter} stub then wins by being the only
 * remaining {@code RateLimiter}, and no filter runs.
 *
 * <p>{@code failOpen} is per limiter: writes/auth fail-CLOSED (abuse surface),
 * redirects fail-OPEN (availability of the core product wins) — ADR-005.
 */
@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        Map<String, LimiterConfig> limiters) {

    public RateLimitProperties {
        limiters = limiters == null ? Map.of() : Map.copyOf(limiters);
    }

    /** {@code null}-safe lookup — returns empty for unknown limiter names. */
    public Optional<LimiterConfig> forName(String name) {
        return Optional.ofNullable(limiters.get(name));
    }

    /**
     * One limiter's configuration.
     * @param capacity        max tokens in the bucket (also the burst tolerance)
     * @param refillPerMinute steady-state rate — capacity/60000 tokens per ms
     * @param failOpen        when Redis is unreachable: {@code true} = allow,
     *                        {@code false} = deny (503-inducing)
     */
    public record LimiterConfig(int capacity, int refillPerMinute, boolean failOpen) {

        public LimiterConfig {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be > 0, got " + capacity);
            }
            if (refillPerMinute <= 0) {
                throw new IllegalArgumentException("refillPerMinute must be > 0, got " + refillPerMinute);
            }
        }

        /** Tokens replenished per millisecond — the script's ARGV[2]. */
        public double refillPerMillisecond() {
            return refillPerMinute / 60_000.0d;
        }
    }
}
