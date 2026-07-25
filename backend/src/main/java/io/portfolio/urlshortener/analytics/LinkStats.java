package io.portfolio.urlshortener.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "link_stats")
public class LinkStats {

    @Id
    @Column(name = "short_code")
    private String shortCode;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Column(name = "last_click_at")
    private Instant lastClickAt;

    @Column(name = "last_referrer", length = 1024)
    private String lastReferrer;

    protected LinkStats() {
    }

    public LinkStats(String shortCode, long clickCount, Instant lastClickAt, String lastReferrer) {
        this.shortCode = shortCode;
        this.clickCount = clickCount;
        this.lastClickAt = lastClickAt;
        this.lastReferrer = lastReferrer;
    }

    public String getShortCode() { return shortCode; }
    public long getClickCount() { return clickCount; }
    public Instant getLastClickAt() { return lastClickAt; }
    public String getLastReferrer() { return lastReferrer; }
}
