package io.portfolio.urlshortener.contracts;

import io.portfolio.urlshortener.contracts.UrlCache.CacheResult;
import io.portfolio.urlshortener.contracts.UrlCache.Hit;
import io.portfolio.urlshortener.contracts.UrlCache.NegativeHit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CONTRACT TEST — every UrlCache implementation (stub or real) must pass.
 * Subclass and override {@link #cache()} to certify an implementation.
 */
@Tag("contract")
public abstract class UrlCacheContractTest {

    protected abstract UrlCache cache();

    /** Whether the implementation actually stores values (stubs may not). */
    protected boolean isStoring() {
        return true;
    }

    @Test
    void getUnknownKeyIsMissOrNegative_neverThrows() {
        assertThatCode(() -> {
            CacheResult r = cache().get("nonexistent-code");
            assertThat(r).isNotInstanceOf(Hit.class);
        }).doesNotThrowAnyException();
    }

    @Test
    void putThenGetRoundTrip() {
        cache().put("abc123", "https://example.com/x", Duration.ofMinutes(5));
        CacheResult r = cache().get("abc123");
        if (isStoring()) {
            assertThat(r).isEqualTo(new Hit("https://example.com/x"));
        }
    }

    @Test
    void negativeCacheIsDistinguishableFromHit() {
        cache().putNegative("ghost-code");
        CacheResult r = cache().get("ghost-code");
        if (isStoring()) {
            assertThat(r).isInstanceOf(NegativeHit.class);
        } else {
            assertThat(r).isNotInstanceOf(Hit.class);
        }
    }

    @Test
    void evictRemovesEntry() {
        cache().put("evict-me", "https://example.com/y", Duration.ofMinutes(5));
        cache().evict("evict-me");
        assertThat(cache().get("evict-me")).isNotInstanceOf(Hit.class);
    }

    @Test
    void lockIsGrantableAndReleasable() {
        String key = "lock-key-" + System.nanoTime();
        assertThat(cache().tryLock(key)).isTrue();
        if (isStoring()) {
            assertThat(cache().tryLock(key)).isFalse(); // second acquisition must fail
        }
        cache().unlock(key);
        assertThat(cache().tryLock(key)).isTrue(); // reacquirable after unlock
        cache().unlock(key);
    }

    @Test
    void writesNeverThrow() {
        assertThatCode(() -> {
            cache().put("k", "https://example.com", Duration.ofSeconds(1));
            cache().putNegative("k2");
            cache().evict("k3");
        }).doesNotThrowAnyException();
    }
}
