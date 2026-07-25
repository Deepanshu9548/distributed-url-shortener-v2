package io.portfolio.urlshortener.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Control-DB users. Lookups happen by {@code emailNormalized} (case-folded)
 * to keep sign-in idempotent to input casing.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailNormalized(String emailNormalized);

    boolean existsByEmailNormalized(String emailNormalized);
}
