package io.portfolio.urlshortener.shortener;

import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.contracts.UrlCache;
import io.portfolio.urlshortener.contracts.UrlCache.CacheResult;
import io.portfolio.urlshortener.contracts.UrlCache.Hit;
import io.portfolio.urlshortener.contracts.UrlCache.NegativeHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Redirect resolution — cache-aside per ADR-004.
 *
 * <ul>
 *   <li>Cache {@code Hit} → redirect, no DB touch.</li>
 *   <li>{@code NegativeHit} → 404 (anti cache-penetration sentinel).</li>
 *   <li>{@code Miss} → stampede gate: the {@code tryLock} winner loads the DB
 *       and populates the cache with TTL = min(24h, time to link expiry);
 *       non-holders retry the cache {@value #NON_HOLDER_RETRIES}×
 *       {@value #DEFAULT_RETRY_DELAY_MS}ms, then read the DB <b>without</b>
 *       populating the cache (no herd of writers).</li>
 *   <li>Unknown or expired code (lock-holder path) → negative-cache + 404.</li>
 *   <li>DB/infra failure → {@link InfraUnavailableException} → 503; never
 *       conflated with 404 (frozen decision).</li>
 * </ul>
 *
 * <p>A {@link ClickEvent} is published fire-and-forget only after the redirect
 * decision succeeds — publish latency and failures never affect the response.
 */
@Service
public class RedirectService {

    static final Duration MAX_CACHE_TTL = Duration.ofHours(24);
    static final int NON_HOLDER_RETRIES = 3;
    static final long DEFAULT_RETRY_DELAY_MS = 50;

    private final UrlCache cache;
    private final LinkRepository links;
    private final ShardRouter router;
    private final EventPublisher publisher;
    private final long retryDelayMs;

    @Autowired
    public RedirectService(UrlCache cache, LinkRepository links,
                           ShardRouter router, EventPublisher publisher) {
        this(cache, links, router, publisher, DEFAULT_RETRY_DELAY_MS);
    }

    RedirectService(UrlCache cache, LinkRepository links, ShardRouter router,
                    EventPublisher publisher, long retryDelayMs) {
        this.cache = cache;
        this.links = links;
        this.router = router;
        this.publisher = publisher;
        this.retryDelayMs = retryDelayMs;
    }

    /**
     * @return the long URL to redirect to (302 Location)
     * @throws NotFoundException          unknown or expired code
     * @throws InfraUnavailableException  DB unreachable and cache can't answer
     */
    public String resolve(String shortCode, String referrer, String userAgent, String requestId) {
        String longUrl = resolveLongUrl(shortCode);
        publisher.publishClick(ClickEvent.of(shortCode, referrer, userAgent, requestId));
        return longUrl;
    }

    private String resolveLongUrl(String shortCode) {
        CacheResult cached = cache.get(shortCode);
        if (cached instanceof Hit hit) {
            return hit.longUrl();
        }
        if (cached instanceof NegativeHit) {
            throw new NotFoundException(shortCode);
        }

        if (cache.tryLock(shortCode)) {
            try {
                return loadAndPopulate(shortCode);
            } finally {
                cache.unlock(shortCode);
            }
        }
        return awaitHolderOrReadThrough(shortCode);
    }

    /** Lock-holder path: single DB load, populate cache (positive or negative). */
    private String loadAndPopulate(String shortCode) {
        Link link = loadFromDb(shortCode);
        Instant now = Instant.now();
        if (link == null || link.isExpired(now)) {
            cache.putNegative(shortCode);
            throw new NotFoundException(shortCode);
        }
        cache.put(shortCode, link.getLongUrl(), cacheTtl(link, now));
        return link.getLongUrl();
    }

    /** Non-holder path: short cache retries, then DB read WITHOUT cache writes. */
    private String awaitHolderOrReadThrough(String shortCode) {
        for (int attempt = 0; attempt < NON_HOLDER_RETRIES; attempt++) {
            if (!sleepQuietly(retryDelayMs)) {
                break;
            }
            CacheResult retried = cache.get(shortCode);
            if (retried instanceof Hit hit) {
                return hit.longUrl();
            }
            if (retried instanceof NegativeHit) {
                throw new NotFoundException(shortCode);
            }
        }
        Link link = loadFromDb(shortCode);
        if (link == null || link.isExpired(Instant.now())) {
            throw new NotFoundException(shortCode);
        }
        return link.getLongUrl();
    }

    private Link loadFromDb(String shortCode) {
        try {
            return router.executeRead(shortCode, () -> links.findByShortCode(shortCode)).orElse(null);
        } catch (RuntimeException e) {
            throw new InfraUnavailableException("link storage unavailable", e);
        }
    }

    private static Duration cacheTtl(Link link, Instant now) {
        if (link.getExpiresAt() == null) {
            return MAX_CACHE_TTL;
        }
        Duration remaining = Duration.between(now, link.getExpiresAt());
        return remaining.compareTo(MAX_CACHE_TTL) < 0 ? remaining : MAX_CACHE_TTL;
    }

    /** @return false when interrupted — caller stops retrying and falls through to the DB */
    private static boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
