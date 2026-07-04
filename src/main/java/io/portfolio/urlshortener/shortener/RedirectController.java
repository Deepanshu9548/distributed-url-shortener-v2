package io.portfolio.urlshortener.shortener;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * The hot path: GET /{shortCode} → 302 (never 301 — ADR-003 keeps every click
 * on our infrastructure for analytics and TTL honesty). The path variable is
 * constrained to the short-code charset {@code [0-9a-zA-Z_-]{1,32}} so it can
 * never shadow {@code /api/**}, {@code /actuator/**} or dotted resource paths.
 */
@RestController
public class RedirectController {

    private final RedirectService redirectService;

    public RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @GetMapping("/{shortCode:[0-9a-zA-Z_-]{1,32}}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            @RequestHeader(value = "Referer", required = false) String referrer,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        String longUrl = redirectService.resolve(shortCode, referrer, userAgent, rid);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }
}
