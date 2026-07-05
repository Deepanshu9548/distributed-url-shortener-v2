package io.portfolio.urlshortener.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Control-DB ownership index. Track D's link-events consumer WRITES rows
 * here (ADR-008); this interface exists so the auth track can READ them for
 * the ownership guard and {@code GET /api/me/links}.
 *
 * <p>Never joined with the shard {@code links} table — the control DB is
 * off the redirect hot path (ADR-010).
 */
public interface LinkIndexRepository extends JpaRepository<UserLink, String> {

    Optional<UserLink> findByShortCodeAndUserId(String shortCode, Long userId);

    Page<UserLink> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
