package io.portfolio.urlshortener.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface LinkStatsRepository extends JpaRepository<LinkStats, String> {
    @Modifying
    @Query(value = """
        INSERT INTO link_stats (short_code, click_count, last_click_at, last_referrer)
        VALUES (:shortCode, 1, :clickedAt, :referrer)
        ON CONFLICT (short_code) DO UPDATE
        SET click_count = link_stats.click_count + 1,
            last_click_at = :clickedAt,
            last_referrer = COALESCE(:referrer, link_stats.last_referrer)
    """, nativeQuery = true)
    void incrementClickCount(@Param("shortCode") String shortCode,
                             @Param("clickedAt") Instant clickedAt,
                             @Param("referrer") String referrer);
}
