package io.portfolio.urlshortener.shortener;

import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.ShardRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Link creation and metadata reads. No long-URL deduplication by design
 * (ADR-011). All repository access is routed through the {@link ShardRouter}:
 * link rows are keyed by short code, idempotency rows by the idempotency key
 * itself (same key → same shard, so client retries land where the first
 * attempt wrote).
 *
 * <p>Create publishes {@code LinkEvent(CREATED)} instead of dual-writing the
 * user_links index (ADR-008).
 */
@Service
public class ShortenService {

    static final Duration IDEMPOTENCY_WINDOW = Duration.ofHours(24);

    private final SnowflakeIdGenerator idGenerator;
    private final UrlValidator validator;
    private final LinkRepository links;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final ShardRouter router;
    private final EventPublisher publisher;
    private final String baseUrl;

    public ShortenService(SnowflakeIdGenerator idGenerator,
                          UrlValidator validator,
                          LinkRepository links,
                          IdempotencyKeyRepository idempotencyKeys,
                          ShardRouter router,
                          EventPublisher publisher,
                          @Value("${app.base-url}") String baseUrl) {
        this.idGenerator = idGenerator;
        this.validator = validator;
        this.links = links;
        this.idempotencyKeys = idempotencyKeys;
        this.router = router;
        this.publisher = publisher;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** {@code replayed} = served from the idempotency table → controller returns 200 not 201. */
    public record CreationResult(LinkResponse link, boolean replayed) {
    }

    public CreationResult create(CreateLinkRequest request, String idempotencyKey, String requestId, Long userId) {
        Instant now = Instant.now();

        if (hasText(idempotencyKey)) {
            Optional<LinkResponse> replay = findReplay(idempotencyKey, now);
            if (replay.isPresent()) {
                return new CreationResult(replay.get(), true);
            }
        }

        validator.validateUrl(request.longUrl());
        Instant expiresAt = resolveExpiry(request, now);

        boolean custom = hasText(request.customAlias());
        
        int maxAttempts = custom ? 1 : 3;
        int attempts = 0;
        String shortCode = null;
        Link link = null;
        
        while (attempts < maxAttempts) {
            attempts++;
            long id = idGenerator.nextId();
            
            if (custom) {
                validator.validateAlias(request.customAlias());
                shortCode = request.customAlias();
                String code = shortCode;
                boolean taken = router.executeRead(code, () -> links.existsByShortCode(code));
                if (taken) {
                    throw new AliasConflictException(code);
                }
            } else {
                shortCode = Base62.encode(id);
            }

            Link finalLink = new Link(id, shortCode, request.longUrl(), userId, now, expiresAt, custom);
            link = finalLink;
            try {
                router.executeWrite(shortCode, () -> links.save(finalLink));
                break; // Success
            } catch (DataIntegrityViolationException e) {
                if (custom || attempts == maxAttempts) {
                    throw new AliasConflictException(shortCode);
                }
                // If not custom and we haven't reached maxAttempts, we loop and try again
            }
        }

        if (hasText(idempotencyKey)) {
            IdempotencyKey record = new IdempotencyKey(idempotencyKey, shortCode, now);
            router.executeWrite(idempotencyKey, () -> idempotencyKeys.save(record));
        }

        publisher.publishLinkEvent(LinkEvent.of(LinkEvent.Type.CREATED, shortCode, userId, requestId));
        return new CreationResult(toResponse(link), false);
    }

    /** Public metadata read; expired links are indistinguishable from unknown (404). */
    public LinkMetadataResponse getLink(String shortCode) {
        Link link = router.executeRead(shortCode, () -> links.findByShortCode(shortCode))
                .orElseThrow(() -> new NotFoundException(shortCode));
        if (link.isExpired(Instant.now())) {
            throw new NotFoundException(shortCode);
        }
        return new LinkMetadataResponse(link.getShortCode(), shortUrlFor(link.getShortCode()),
                link.getLongUrl(), link.getCreatedAt(), link.getExpiresAt(), link.isCustomAlias());
    }

    private Optional<LinkResponse> findReplay(String idempotencyKey, Instant now) {
        Instant windowStart = now.minus(IDEMPOTENCY_WINDOW);
        return router.executeRead(idempotencyKey, () -> idempotencyKeys.findById(idempotencyKey))
                .filter(k -> k.getCreatedAt().isAfter(windowStart))
                .flatMap(k -> router.executeRead(k.getShortCode(), () -> links.findByShortCode(k.getShortCode())))
                .map(this::toResponse);
    }

    private Instant resolveExpiry(CreateLinkRequest request, Instant now) {
        if (request.expiresAt() != null) {
            if (!request.expiresAt().isAfter(now)) {
                throw new ValidationException("expiresAt must be in the future");
            }
            return request.expiresAt();
        }
        if (request.ttlSeconds() != null) {
            if (request.ttlSeconds() <= 0) {
                throw new ValidationException("ttlSeconds must be positive");
            }
            return now.plusSeconds(request.ttlSeconds());
        }
        return null;
    }

    private LinkResponse toResponse(Link link) {
        return new LinkResponse(link.getShortCode(), shortUrlFor(link.getShortCode()),
                link.getLongUrl(), link.getExpiresAt());
    }

    private String shortUrlFor(String shortCode) {
        return baseUrl + "/" + shortCode;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
