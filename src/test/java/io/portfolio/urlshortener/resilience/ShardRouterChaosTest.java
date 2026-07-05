package io.portfolio.urlshortener.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.portfolio.urlshortener.sharding.ConsistentHashRing;
import io.portfolio.urlshortener.sharding.HashRingShardRouter;
import io.portfolio.urlshortener.sharding.ShardProperties;
import io.portfolio.urlshortener.sharding.ShardUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("chaos")
public class ShardRouterChaosTest {

    private CircuitBreakerRegistry registry;
    private SimpleMeterRegistry meterRegistry;
    private HashRingShardRouter router;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        // pre-create breakers so we can manipulate them
        CircuitBreaker breaker1 = registry.circuitBreaker("shard-shard1", io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50.0f)
            .build());
        CircuitBreaker breaker2 = registry.circuitBreaker("shard-shard2", io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50.0f)
            .build());
            
        meterRegistry = new SimpleMeterRegistry();
        
        ShardProperties props = new ShardProperties(true, 150, List.of(
            new ShardProperties.Shard("shard1", null, null),
            new ShardProperties.Shard("shard2", null, null)
        ));
        
        router = new HashRingShardRouter(props, meterRegistry, registry);
    }

    @Test
    void testShardRouterCircuitBreaker() {
        CircuitBreaker breaker1 = registry.circuitBreaker("shard-shard1");
        
        // Find a key for shard1
        String shard1Key = "key1";
        while (!router.shardFor(shard1Key).equals("shard1")) {
            shard1Key += "1";
        }
        
        // Find a key for shard2
        String shard2Key = "key2";
        while (!router.shardFor(shard2Key).equals("shard2")) {
            shard2Key += "2";
        }

        Supplier<String> failingSupplier = () -> {
            throw new DataAccessResourceFailureException("DB down");
        };

        // Trip shard1 breaker via reads
        for (int i = 0; i < 10; i++) {
            try {
                router.executeRead(shard1Key, failingSupplier);
            } catch (Exception ignored) {}
        }

        assertThat(breaker1.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Subsequent call fails fast with ShardUnavailableException
        final String finalKey1 = shard1Key;
        assertThatThrownBy(() -> router.executeWrite(finalKey1, () -> "value"))
                .isInstanceOf(ShardUnavailableException.class)
                .hasMessageContaining("shard1");

        // Verify fallback metric
        double fallbackCount = meterRegistry.get("resilience.fallback.total")
                .tag("breaker", "shard-shard1")
                .tag("outcome", "fallback")
                .counter().count();
        assertThat(fallbackCount).isGreaterThan(0);

        // shard2 remains unaffected
        String result = router.executeWrite(shard2Key, () -> "success");
        assertThat(result).isEqualTo("success");
    }
}
