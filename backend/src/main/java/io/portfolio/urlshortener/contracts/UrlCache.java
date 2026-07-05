package io.portfolio.urlshortener.contracts;

import java.time.Duration;

/**
 * CONTRACT — frozen post-M0. Cache-aside cache for shortCode → longUrl.
 *
 * <p>Implementations MUST degrade, never fail: any backing-store error is
 * translated into {@link Miss} (reads) or a silent no-op (writes), so the
 * redirect path can always fall through to the database (ADR-004).
 */
public interface UrlCache {

    sealed interface CacheResult permits Hit, NegativeHit, Miss {}

    /** Cached long URL found. */
    record Hit(String longUrl) implements CacheResult {}

    /** Code is cached as known-missing (negative cache) — treat as 404. */
    record NegativeHit() implements CacheResult {}

    /** Not in cache (or cache unavailable) — caller must consult the DB. */
    record Miss() implements CacheResult {}

    CacheResult get(String shortCode);

    void put(String shortCode, String longUrl, Duration ttl);

    /** Cache "does not exist" with a short TTL (anti cache-penetration). */
    void putNegative(String shortCode);

    void evict(String shortCode);

    /** Per-key stampede lock. True = caller is the designated DB loader. */
    boolean tryLock(String shortCode);

    void unlock(String shortCode);
}
