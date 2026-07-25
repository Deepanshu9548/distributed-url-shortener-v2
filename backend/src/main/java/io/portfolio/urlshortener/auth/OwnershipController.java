package io.portfolio.urlshortener.auth;

import io.portfolio.urlshortener.auth.AuthDtos.LinkMetaResponse;
import io.portfolio.urlshortener.auth.AuthDtos.LinkUpdateRequest;
import io.portfolio.urlshortener.auth.AuthDtos.UserLinkView;
import io.portfolio.urlshortener.auth.AuthDtos.UserLinksPage;
import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.shortener.Link;
import io.portfolio.urlshortener.shortener.LinkRepository;
import io.portfolio.urlshortener.shortener.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Ownership-scoped operations on links.
 *
 * <ul>
 *   <li>{@code GET  /api/me/links} — the caller's short-code index, paged.
 *       Reads {@link LinkIndexRepository} (control DB — off the shard hot
 *       path per ADR-010).</li>
 *   <li>{@code PUT  /api/links/{code}} — caller must own {@code code}; only
 *       {@code longUrl} and {@code expiresAt}/{@code ttlSeconds} are
 *       mutable. Publishes {@link LinkEvent.Type#UPDATED}.</li>
 *   <li>{@code DELETE /api/links/{code}} — owner-only. Publishes
 *       {@link LinkEvent.Type#DELETED}.</li>
 * </ul>
 *
 * <p>Ownership misses return {@code 404} (not 403) so the endpoint doesn't
 * leak the existence of other users' codes.
 */
@RestController
public class OwnershipController {

    private final LinkIndexRepository linkIndex;
    private final LinkRepository linkRepository;
    private final ShardRouter shardRouter;
    private final EventPublisher events;

    public OwnershipController(LinkIndexRepository linkIndex,
                               LinkRepository linkRepository,
                               ShardRouter shardRouter,
                               EventPublisher events) {
        this.linkIndex = linkIndex;
        this.linkRepository = linkRepository;
        this.shardRouter = shardRouter;
        this.events = events;
    }

    @GetMapping("/api/me/links")
    public UserLinksPage myLinks(AuthenticatedUser current,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<UserLink> result = linkIndex.findByUserIdOrderByCreatedAtDesc(
                current.userId(), PageRequest.of(safePage, safeSize));
        return new UserLinksPage(
                result.getContent().stream()
                        .map(l -> new UserLinkView(l.getShortCode(), l.getCreatedAt()))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @PutMapping("/api/links/{code}")
    public LinkMetaResponse update(@PathVariable String code,
                                   @Valid @RequestBody LinkUpdateRequest request,
                                   AuthenticatedUser current) {
        requireOwnership(code, current.userId());

        Instant newExpiry = request.expiresAt() != null
                ? request.expiresAt()
                : (request.ttlSeconds() != null ? Instant.now().plusSeconds(request.ttlSeconds()) : null);

        Link saved = shardRouter.executeWrite(code, () -> {
            Link link = linkRepository.findByShortCode(code).orElseThrow(() -> new NotFoundException(code));
            if (request.longUrl() != null && !request.longUrl().isBlank()) {
                link.setLongUrl(request.longUrl());
            }
            if (newExpiry != null || (request.expiresAt() == null && request.ttlSeconds() == null)) {
                // Only mutate when the caller supplied one — leave it alone otherwise.
                if (newExpiry != null) {
                    link.setExpiresAt(newExpiry);
                }
            }
            return linkRepository.save(link);
        });

        events.publishLinkEvent(LinkEvent.of(LinkEvent.Type.UPDATED, code, current.userId(), null));

        return new LinkMetaResponse(
                saved.getShortCode(), saved.getLongUrl(), saved.getCreatedAt(), saved.getExpiresAt());
    }

    @DeleteMapping("/api/links/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code, AuthenticatedUser current) {
        requireOwnership(code, current.userId());

        shardRouter.executeWrite(code, () -> {
            linkRepository.findByShortCode(code).ifPresent(linkRepository::delete);
            return null;
        });

        events.publishLinkEvent(LinkEvent.of(LinkEvent.Type.DELETED, code, current.userId(), null));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void requireOwnership(String code, long userId) {
        linkIndex.findByShortCodeAndUserId(code, userId).orElseThrow(() -> new NotFoundException(code));
    }
}
