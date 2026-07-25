package io.portfolio.urlshortener.shortener;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Link management API. 201 on create, 200 on Idempotency-Key replay,
 * 400 validation, 409 alias conflict — bodies via GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final ShortenService shortenService;

    public LinkController(ShortenService shortenService) {
        this.shortenService = shortenService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(
            @RequestBody CreateLinkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        
        Long userId = null;
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String principalStr) {
            try {
                userId = Long.parseLong(principalStr);
            } catch (NumberFormatException ignored) {}
        }

        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        ShortenService.CreationResult result = shortenService.create(request, idempotencyKey, rid, userId);
        return ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.link());
    }

    @GetMapping("/{shortCode}")
    public LinkMetadataResponse get(@PathVariable String shortCode) {
        return shortenService.getLink(shortCode);
    }
}
