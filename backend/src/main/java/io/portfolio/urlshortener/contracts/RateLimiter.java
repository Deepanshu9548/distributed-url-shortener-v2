package io.portfolio.urlshortener.contracts;

/**
 * CONTRACT — frozen post-M0. Distributed rate limiting.
 *
 * <p>Implementations MUST be atomic across app instances (state lives in a
 * shared store, check-and-decrement is race-free) and MUST honor the per-limiter
 * failure policy when the store is unavailable: {@code failOpen=true} limiters
 * allow, {@code failOpen=false} limiters deny (ADR-005).
 */
public interface RateLimiter {

    /**
     * @param key         fully-qualified bucket key, e.g. {@code rl:{user:42}:write}.
     *                    Hash-tag braces keep the key cluster-slot-safe.
     * @param limiterName configured limiter ("write", "redirect", "auth").
     */
    RateLimitResult check(String key, String limiterName);

    /**
     * @param allowed          whether the request may proceed
     * @param retryAfterMillis when denied: how long until a token is available
     * @param storeUnavailable true when the decision came from the failure
     *                         policy rather than the store (drives 503-vs-429)
     */
    record RateLimitResult(boolean allowed, long retryAfterMillis, boolean storeUnavailable) {

        public static RateLimitResult allowedResult() {
            return new RateLimitResult(true, 0, false);
        }

        public static RateLimitResult denied(long retryAfterMillis) {
            return new RateLimitResult(false, retryAfterMillis, false);
        }

        public static RateLimitResult failOpen() {
            return new RateLimitResult(true, 0, true);
        }

        public static RateLimitResult failClosed() {
            return new RateLimitResult(false, 0, true);
        }
    }
}
