package io.portfolio.urlshortener.contracts.stubs;

import io.portfolio.urlshortener.contracts.RateLimiter;
import org.springframework.stereotype.Component;

/**
 * STUB — allows everything. Also reused (intentionally) as the implementation
 * behind the {@code ratelimit.enabled=false} load-test toggle.
 */
@Component
public class AllowAllRateLimiter implements RateLimiter {

    @Override
    public RateLimitResult check(String key, String limiterName) {
        return RateLimitResult.allowedResult();
    }
}
