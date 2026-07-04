package io.portfolio.urlshortener.shortener;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Sharded repository — every call MUST run inside
 * {@code ShardRouter.executeRead/executeWrite} keyed by the short code so the
 * routing datasource (Track B) can pick the owning shard.
 */
public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}
