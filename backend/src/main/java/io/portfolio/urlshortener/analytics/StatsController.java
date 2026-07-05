package io.portfolio.urlshortener.analytics;

import io.portfolio.urlshortener.auth.AuthenticatedUser;
import io.portfolio.urlshortener.auth.LinkIndexRepository;
import io.portfolio.urlshortener.shortener.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/links")
public class StatsController {

    private final LinkIndexRepository linkIndexRepository;
    private final LinkStatsRepository linkStatsRepository;

    public StatsController(LinkIndexRepository linkIndexRepository, LinkStatsRepository linkStatsRepository) {
        this.linkIndexRepository = linkIndexRepository;
        this.linkStatsRepository = linkStatsRepository;
    }

    @GetMapping("/{code}/stats")
    public StatsResponse getStats(@PathVariable String code, AuthenticatedUser currentUser) {
        // Ownership check: if the link is not in the index for this user, return 404
        linkIndexRepository.findByShortCodeAndUserId(code, currentUser.userId())
                .orElseThrow(() -> new NotFoundException(code));

        LinkStats stats = linkStatsRepository.findById(code)
                .orElse(new LinkStats(code, 0, null, null)); // Return zero stats if none exist

        return new StatsResponse(stats.getShortCode(), stats.getClickCount(), stats.getLastClickAt());
    }

    public record StatsResponse(String shortCode, long clickCount, Instant lastClickAt) {}
}
