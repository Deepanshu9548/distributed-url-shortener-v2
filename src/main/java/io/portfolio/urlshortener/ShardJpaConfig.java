package io.portfolio.urlshortener;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scopes Spring Boot's DEFAULT (shard-side) JPA stack to the shortener
 * package only, so the auth entities — which live in the control DB with
 * their own EMF/TxManager (ADR-010, {@code auth.ControlDbConfig}) — are not
 * picked up by the default EntityManagerFactory.
 *
 * <p>Lives in its own {@code @Configuration} (not on the application class)
 * so {@code @WebMvcTest} slices, which use the app class as context root,
 * don't try to bootstrap JPA repositories.
 */
@Configuration
@EntityScan(basePackages = "io.portfolio.urlshortener.shortener")
@EnableJpaRepositories(basePackages = "io.portfolio.urlshortener.shortener")
public class ShardJpaConfig {
}
