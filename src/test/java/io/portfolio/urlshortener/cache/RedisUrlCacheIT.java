package io.portfolio.urlshortener.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.portfolio.urlshortener.contracts.UrlCache;
import io.portfolio.urlshortener.contracts.UrlCacheContractTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Certifies {@link RedisUrlCache} against the frozen UrlCache contract using
 * real Redis (Testcontainers). Runs in CI via {@code -Pdocker-tests}; excluded
 * locally where Docker is absent. No testcontainers redis module exists —
 * a GenericContainer on redis:7-alpine suffices.
 */
@Tag("docker")
@Testcontainers
class RedisUrlCacheIT extends UrlCacheContractTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisUrlCache cache;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        cache = new RedisUrlCache(new StringRedisTemplate(connectionFactory), io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults(), new SimpleMeterRegistry());
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Override
    protected UrlCache cache() {
        return cache;
    }
}
