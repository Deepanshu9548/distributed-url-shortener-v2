package io.portfolio.urlshortener.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface RawClickEventRepository extends JpaRepository<RawClickEvent, UUID> {
    @Modifying
    @Query(value = "INSERT INTO raw_click_events (event_id, created_at) VALUES (:eventId, :createdAt) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertIgnore(@Param("eventId") UUID eventId, @Param("createdAt") Instant createdAt);
}
