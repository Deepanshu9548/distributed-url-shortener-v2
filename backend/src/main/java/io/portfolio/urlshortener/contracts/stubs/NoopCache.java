package io.portfolio.urlshortener.contracts.stubs;

import io.portfolio.urlshortener.contracts.UrlCache;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * STUB — always misses, accepts writes silently, always grants the lock.
 * Semantically identical to "Redis permanently down", which is exactly the
 * degradation the real cache must exhibit — so code written against this stub
 * is automatically correct against cache failure.
 */
@Component
public class NoopCache implements UrlCache {

    @Override
    public CacheResult get(String shortCode) {
        return new Miss();
    }

    @Override
    public void put(String shortCode, String longUrl, Duration ttl) {
        // no-op
    }

    @Override
    public void putNegative(String shortCode) {
        // no-op
    }

    @Override
    public void evict(String shortCode) {
        // no-op
    }

    @Override
    public boolean tryLock(String shortCode) {
        return true;
    }

    @Override
    public void unlock(String shortCode) {
        // no-op
    }
}
