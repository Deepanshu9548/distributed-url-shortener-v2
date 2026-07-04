package io.portfolio.urlshortener.shortener;

import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.ShardRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShortenServiceTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String LONG_URL = "https://example.com/some/long/path";
    private static final String REQUEST_ID = "req-123";

    @Mock
    private LinkRepository links;
    @Mock
    private IdempotencyKeyRepository idempotencyKeys;
    @Mock
    private ShardRouter router;
    @Mock
    private EventPublisher publisher;

    private ShortenService service;

    @BeforeEach
    void setUp() {
        // router mock passes suppliers straight through, like the M0 stub
        lenient().when(router.executeRead(anyString(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
        lenient().when(router.executeWrite(anyString(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
        lenient().when(links.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(links.findByShortCode(anyString())).thenReturn(Optional.empty());
        lenient().when(links.existsByShortCode(anyString())).thenReturn(false);
        lenient().when(idempotencyKeys.findById(anyString())).thenReturn(Optional.empty());
        lenient().when(idempotencyKeys.save(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new ShortenService(new SnowflakeIdGenerator(1), new UrlValidator(),
                links, idempotencyKeys, router, publisher, BASE_URL);
    }

    private static CreateLinkRequest request(String longUrl) {
        return new CreateLinkRequest(longUrl, null, null, null);
    }

    @Test
    void createsLinkWithBase62CodeAndPublishesCreatedEvent() {
        var result = service.create(request(LONG_URL), null, REQUEST_ID);

        assertThat(result.replayed()).isFalse();
        assertThat(result.link().longUrl()).isEqualTo(LONG_URL);
        assertThat(result.link().shortCode()).matches("[0-9a-zA-Z]+");
        assertThat(result.link().shortUrl()).isEqualTo(BASE_URL + "/" + result.link().shortCode());
        assertThat(result.link().expiresAt()).isNull();

        ArgumentCaptor<Link> saved = ArgumentCaptor.forClass(Link.class);
        verify(links).save(saved.capture());
        assertThat(Base62.encode(saved.getValue().getId())).isEqualTo(result.link().shortCode());
        assertThat(saved.getValue().isCustomAlias()).isFalse();

        ArgumentCaptor<LinkEvent> event = ArgumentCaptor.forClass(LinkEvent.class);
        verify(publisher).publishLinkEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(LinkEvent.Type.CREATED);
        assertThat(event.getValue().shortCode()).isEqualTo(result.link().shortCode());
        assertThat(event.getValue().requestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    void routesWriteByShortCode() {
        var result = service.create(request(LONG_URL), null, REQUEST_ID);
        verify(router).executeWrite(eq(result.link().shortCode()), any());
    }

    @Test
    void sameLongUrlTwiceYieldsDistinctCodes_noDedup() {
        var first = service.create(request(LONG_URL), null, REQUEST_ID);
        var second = service.create(request(LONG_URL), null, REQUEST_ID);
        assertThat(first.link().shortCode()).isNotEqualTo(second.link().shortCode());
    }

    @Test
    void invalidUrlRejectedBeforeAnyPersistence() {
        assertThatThrownBy(() -> service.create(request("ftp://example.com"), null, REQUEST_ID))
                .isInstanceOf(ValidationException.class);
        verify(links, never()).save(any());
        verify(publisher, never()).publishLinkEvent(any());
    }

    @Test
    void ttlSecondsBecomesExpiresAt() {
        var result = service.create(new CreateLinkRequest(LONG_URL, null, null, 3600L), null, REQUEST_ID);
        assertThat(result.link().expiresAt())
                .isCloseTo(Instant.now().plusSeconds(3600), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void explicitExpiresAtWinsOverTtl() {
        Instant explicit = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        var result = service.create(new CreateLinkRequest(LONG_URL, null, explicit, 60L), null, REQUEST_ID);
        assertThat(result.link().expiresAt()).isEqualTo(explicit);
    }

    @Test
    void pastExpiresAtAndNonPositiveTtlAreRejected() {
        assertThatThrownBy(() -> service.create(
                new CreateLinkRequest(LONG_URL, null, Instant.now().minusSeconds(60), null), null, REQUEST_ID))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(
                new CreateLinkRequest(LONG_URL, null, null, 0L), null, REQUEST_ID))
                .isInstanceOf(ValidationException.class);
    }

    // --- custom alias ---

    @Test
    void customAliasIsUsedAsShortCode() {
        var result = service.create(new CreateLinkRequest(LONG_URL, "my-alias", null, null), null, REQUEST_ID);
        assertThat(result.link().shortCode()).isEqualTo("my-alias");
        ArgumentCaptor<Link> saved = ArgumentCaptor.forClass(Link.class);
        verify(links).save(saved.capture());
        assertThat(saved.getValue().isCustomAlias()).isTrue();
    }

    @Test
    void takenAliasThrowsConflict() {
        when(links.existsByShortCode("my-alias")).thenReturn(true);
        assertThatThrownBy(() -> service.create(
                new CreateLinkRequest(LONG_URL, "my-alias", null, null), null, REQUEST_ID))
                .isInstanceOf(AliasConflictException.class);
        verify(links, never()).save(any());
    }

    @Test
    void aliasUniquenessRaceLostAtInsertThrowsConflict() {
        when(links.save(any(Link.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));
        assertThatThrownBy(() -> service.create(
                new CreateLinkRequest(LONG_URL, "my-alias", null, null), null, REQUEST_ID))
                .isInstanceOf(AliasConflictException.class);
        verify(publisher, never()).publishLinkEvent(any());
    }

    @Test
    void blocklistedAliasRejected() {
        assertThatThrownBy(() -> service.create(
                new CreateLinkRequest(LONG_URL, "actuator", null, null), null, REQUEST_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reserved");
        verify(links, never()).save(any());
    }

    // --- idempotency ---

    @Test
    void idempotencyKeyReplayReturnsExistingLinkWithoutCreating() {
        Instant created = Instant.now().minusSeconds(60);
        when(idempotencyKeys.findById("idem-1"))
                .thenReturn(Optional.of(new IdempotencyKey("idem-1", "abc123", created)));
        when(links.findByShortCode("abc123"))
                .thenReturn(Optional.of(new Link(1L, "abc123", LONG_URL, null, created, null, false)));

        var result = service.create(request(LONG_URL), "idem-1", REQUEST_ID);

        assertThat(result.replayed()).isTrue();
        assertThat(result.link().shortCode()).isEqualTo("abc123");
        verify(links, never()).save(any());
        verify(publisher, never()).publishLinkEvent(any());
    }

    @Test
    void staleIdempotencyKeyOutside24hWindowIsIgnored() {
        Instant stale = Instant.now().minus(25, ChronoUnit.HOURS);
        when(idempotencyKeys.findById("idem-old"))
                .thenReturn(Optional.of(new IdempotencyKey("idem-old", "old123", stale)));

        var result = service.create(request(LONG_URL), "idem-old", REQUEST_ID);

        assertThat(result.replayed()).isFalse();
        verify(links).save(any(Link.class));
    }

    @Test
    void freshCreateWithIdempotencyKeyStoresTheKey() {
        var result = service.create(request(LONG_URL), "idem-new", REQUEST_ID);

        ArgumentCaptor<IdempotencyKey> stored = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyKeys).save(stored.capture());
        assertThat(stored.getValue().getKey()).isEqualTo("idem-new");
        assertThat(stored.getValue().getShortCode()).isEqualTo(result.link().shortCode());
        // idempotency rows are routed by the key itself
        verify(router).executeWrite(eq("idem-new"), any());
    }

    // --- metadata read ---

    @Test
    void getLinkReturnsMetadata() {
        Instant created = Instant.now().minusSeconds(5);
        when(links.findByShortCode("abc123"))
                .thenReturn(Optional.of(new Link(1L, "abc123", LONG_URL, null, created, null, true)));

        LinkMetadataResponse meta = service.getLink("abc123");

        assertThat(meta.shortCode()).isEqualTo("abc123");
        assertThat(meta.shortUrl()).isEqualTo(BASE_URL + "/abc123");
        assertThat(meta.longUrl()).isEqualTo(LONG_URL);
        assertThat(meta.customAlias()).isTrue();
    }

    @Test
    void getLinkUnknownOrExpiredThrowsNotFound() {
        when(links.findByShortCode("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getLink("nope")).isInstanceOf(NotFoundException.class);

        when(links.findByShortCode("gone")).thenReturn(Optional.of(new Link(
                2L, "gone", LONG_URL, null, Instant.now().minusSeconds(600),
                Instant.now().minusSeconds(60), false)));
        assertThatThrownBy(() -> service.getLink("gone")).isInstanceOf(NotFoundException.class);
    }
}
