package io.portfolio.urlshortener.shortener;

import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.contracts.UrlCache;
import io.portfolio.urlshortener.contracts.UrlCache.Hit;
import io.portfolio.urlshortener.contracts.UrlCache.Miss;
import io.portfolio.urlshortener.contracts.UrlCache.NegativeHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedirectServiceTest {

    private static final String CODE = "abc123";
    private static final String LONG_URL = "https://example.com/target";

    @Mock
    private UrlCache cache;
    @Mock
    private LinkRepository links;
    @Mock
    private ShardRouter router;
    @Mock
    private EventPublisher publisher;

    private RedirectService service;

    @BeforeEach
    void setUp() {
        lenient().when(router.executeRead(anyString(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
        // 1ms retry delay keeps non-holder tests fast
        service = new RedirectService(cache, links, router, publisher, 1);
    }

    private static Link link(Instant expiresAt) {
        return new Link(1L, CODE, LONG_URL, null, Instant.now().minusSeconds(60), expiresAt, false);
    }

    @Test
    void cacheHitRedirectsWithoutTouchingDbAndPublishesClick() {
        when(cache.get(CODE)).thenReturn(new Hit(LONG_URL));

        String url = service.resolve(CODE, "https://ref.example", "TestUA/1.0", "req-9");

        assertThat(url).isEqualTo(LONG_URL);
        verifyNoInteractions(router, links);

        ArgumentCaptor<ClickEvent> click = ArgumentCaptor.forClass(ClickEvent.class);
        verify(publisher).publishClick(click.capture());
        assertThat(click.getValue().shortCode()).isEqualTo(CODE);
        assertThat(click.getValue().referrer()).isEqualTo("https://ref.example");
        assertThat(click.getValue().userAgent()).isEqualTo("TestUA/1.0");
        assertThat(click.getValue().requestId()).isEqualTo("req-9");
        assertThat(click.getValue().eventId()).isNotNull();
    }

    @Test
    void negativeHitIs404WithoutDbAndWithoutClickEvent() {
        when(cache.get(CODE)).thenReturn(new NegativeHit());

        assertThatThrownBy(() -> service.resolve(CODE, null, null, "req"))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(router, links, publisher);
    }

    @Test
    void missWithLockLoadsDbAndCachesWith24hCapWhenNoExpiry() {
        when(cache.get(CODE)).thenReturn(new Miss());
        when(cache.tryLock(CODE)).thenReturn(true);
        when(links.findByShortCode(CODE)).thenReturn(Optional.of(link(null)));

        String url = service.resolve(CODE, null, null, "req");

        assertThat(url).isEqualTo(LONG_URL);
        verify(cache).put(CODE, LONG_URL, Duration.ofHours(24));
        verify(cache).unlock(CODE);
        verify(publisher).publishClick(any());
    }

    @Test
    void cacheTtlIsRemainingTimeToExpiryWhenSooner() {
        when(cache.get(CODE)).thenReturn(new Miss());
        when(cache.tryLock(CODE)).thenReturn(true);
        when(links.findByShortCode(CODE)).thenReturn(Optional.of(link(Instant.now().plus(1, ChronoUnit.HOURS))));

        service.resolve(CODE, null, null, "req");

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(cache).put(eq(CODE), eq(LONG_URL), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofMinutes(59), Duration.ofHours(1));
    }

    @Test
    void unknownCodeGetsNegativeCachedAnd404() {
        when(cache.get(CODE)).thenReturn(new Miss());
        when(cache.tryLock(CODE)).thenReturn(true);
        when(links.findByShortCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(CODE, null, null, "req"))
                .isInstanceOf(NotFoundException.class);

        verify(cache).putNegative(CODE);
        verify(cache).unlock(CODE);
        verify(cache, never()).put(anyString(), anyString(), any());
        verify(publisher, never()).publishClick(any());
    }

    @Test
    void expiredLinkIsNegativeCachedAnd404() {
        when(cache.get(CODE)).thenReturn(new Miss());
        when(cache.tryLock(CODE)).thenReturn(true);
        when(links.findByShortCode(CODE)).thenReturn(Optional.of(link(Instant.now().minusSeconds(30))));

        assertThatThrownBy(() -> service.resolve(CODE, null, null, "req"))
                .isInstanceOf(NotFoundException.class);

        verify(cache).putNegative(CODE);
        verify(publisher, never()).publishClick(any());
    }

    @Test
    void nonHolderPicksUpCacheOnRetryAndNeverWritesCache() {
        when(cache.tryLock(CODE)).thenReturn(false);
        when(cache.get(CODE))
                .thenReturn(new Miss())           // initial read
                .thenReturn(new Miss())           // retry 1
                .thenReturn(new Hit(LONG_URL));   // retry 2 — holder populated it

        String url = service.resolve(CODE, null, null, "req");

        assertThat(url).isEqualTo(LONG_URL);
        verify(cache, never()).put(anyString(), anyString(), any());
        verify(cache, never()).putNegative(anyString());
        verify(cache, never()).unlock(anyString());
        verifyNoInteractions(links, router);
    }

    @Test
    void nonHolderFallsThroughToDbWithoutPopulatingCache() {
        when(cache.get(CODE)).thenReturn(new Miss());
        when(cache.tryLock(CODE)).thenReturn(false);
        when(links.findByShortCode(CODE)).thenReturn(Optional.of(link(null)));

        String url = service.resolve(CODE, null, null, "req");

        assertThat(url).isEqualTo(LONG_URL);
        verify(cache, times(1 + RedirectService.NON_HOLDER_RETRIES)).get(CODE);
        verify(cache, never()).put(anyString(), anyString(), any());
        verify(cache, never()).putNegative(anyString());
        verify(publisher).publishClick(any());
    }

    @Test
    void nonHolderSeeingNegativeOnRetryGets404() {
        when(cache.tryLock(CODE)).thenReturn(false);
        when(cache.get(CODE))
                .thenReturn(new Miss())
                .thenReturn(new NegativeHit());

        assertThatThrownBy(() -> service.resolve(CODE, null, null, "req"))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(links, router);
    }

    @Test
    void nonHolderUnknownCodeIs404WithoutNegativeCaching() {
        when(cache.get(CODE)).thenReturn(new Miss());
        when(cache.tryLock(CODE)).thenReturn(false);
        when(links.findByShortCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(CODE, null, null, "req"))
                .isInstanceOf(NotFoundException.class);
        verify(cache, never()).putNegative(anyString());
    }

    @Test
    void dbFailureIsInfraUnavailableNever404() {
        when(cache.get(CODE)).thenReturn(new Miss());
        when(cache.tryLock(CODE)).thenReturn(true);
        when(links.findByShortCode(CODE)).thenThrow(new QueryTimeoutException("db down"));

        assertThatThrownBy(() -> service.resolve(CODE, null, null, "req"))
                .isInstanceOf(InfraUnavailableException.class)
                .isNotInstanceOf(NotFoundException.class);

        verify(cache).unlock(CODE);                    // lock released in finally
        verify(cache, never()).putNegative(anyString()); // "unknown" must not be cached on failure
        verify(publisher, never()).publishClick(any());
    }

    @Test
    void clickEventPublishedOnlyAfterSuccessfulDecision() {
        when(cache.get(CODE)).thenReturn(new Miss());
        when(cache.tryLock(CODE)).thenReturn(true);
        when(links.findByShortCode(CODE)).thenReturn(Optional.of(link(null)));

        service.resolve(CODE, "r", "ua", "rid");

        ArgumentCaptor<ClickEvent> click = ArgumentCaptor.forClass(ClickEvent.class);
        verify(publisher).publishClick(click.capture());
        assertThat(click.getValue().requestId()).isEqualTo("rid");
        assertThat(click.getValue().timestamp()).isNotNull();
    }
}
