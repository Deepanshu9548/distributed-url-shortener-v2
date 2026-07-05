package io.portfolio.urlshortener.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "raw_link_events")
public class RawLinkEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RawLinkEvent() {
    }

    public RawLinkEvent(UUID eventId, Instant createdAt) {
        this.eventId = eventId;
        this.createdAt = createdAt;
    }

    public UUID getEventId() { return eventId; }
    public Instant getCreatedAt() { return createdAt; }
}
