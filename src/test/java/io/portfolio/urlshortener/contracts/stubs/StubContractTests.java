package io.portfolio.urlshortener.contracts.stubs;

import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.contracts.ShardRouterContractTest;
import io.portfolio.urlshortener.contracts.UrlCache;
import io.portfolio.urlshortener.contracts.UrlCacheContractTest;
import io.portfolio.urlshortener.contracts.RateLimiter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Certifies the M0 stubs against the frozen contracts. Real implementations
 * (Tracks A/B/E) must add their own subclasses of the same contract tests.
 */
class StubContractTests {

    @Nested
    class NoopCacheContract extends UrlCacheContractTest {
        private final NoopCache cache = new NoopCache();

        @Override
        protected UrlCache cache() {
            return cache;
        }

        @Override
        protected boolean isStoring() {
            return false;
        }
    }

    @Nested
    class SingleShardRouterContract extends ShardRouterContractTest {
        private final SingleShardRouter router = new SingleShardRouter();

        @Override
        protected ShardRouter router() {
            return router;
        }
    }

    @Nested
    @Tag("contract")
    class AllowAllRateLimiterContract {
        @Test
        void alwaysAllows() {
            RateLimiter limiter = new AllowAllRateLimiter();
            RateLimiter.RateLimitResult r = limiter.check("rl:{ip:1.2.3.4}:write", "write");
            assertThat(r.allowed()).isTrue();
            assertThat(r.storeUnavailable()).isFalse();
        }
    }
}
