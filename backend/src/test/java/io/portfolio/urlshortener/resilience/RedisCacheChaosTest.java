package io.portfolio.urlshortener.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.portfolio.urlshortener.cache.RedisUrlCache;
import io.portfolio.urlshortener.contracts.UrlCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("chaos")
public class RedisCacheChaosTest {

    private CircuitBreakerRegistry registry;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisUrlCache cache;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        // Configure specific instance like application.yml
        CircuitBreaker breaker = registry.circuitBreaker("redis-cache", io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowSize(20)
            .failureRateThreshold(50.0f)
            .permittedNumberOfCallsInHalfOpenState(3)
            .build());
            
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        meterRegistry = new SimpleMeterRegistry();
        cache = new RedisUrlCache(redisTemplate, registry, meterRegistry);
    }

    @Test
    void testCircuitBreakerOpensAndCloses() {
        CircuitBreaker breaker = registry.circuitBreaker("redis-cache");

        // Force failures to trip breaker
        when(valueOps.get(any())).thenThrow(new RedisConnectionFailureException("Redis down"));
        
        for (int i = 0; i < 20; i++) {
            UrlCache.CacheResult result = cache.get("abc");
            assertThat(result).isInstanceOf(UrlCache.Miss.class);
        }
        
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // While OPEN, it short-circuits
        reset(valueOps);
        when(valueOps.get(any())).thenReturn("http://long.url"); // Should not be reached
        UrlCache.CacheResult openResult = cache.get("abc");
        assertThat(openResult).isInstanceOf(UrlCache.Miss.class);
        verifyNoInteractions(valueOps);
        
        // Check metric for fallback
        double fallbackCount = meterRegistry.get("resilience.fallback.total")
                .tag("breaker", "redis-cache")
                .tag("outcome", "fallback")
                .counter().count();
        assertThat(fallbackCount).isGreaterThan(0);

        // Transition to half-open manually to avoid Thread.sleep(5000)
        breaker.transitionToHalfOpenState();
        
        // Should probe and close the breaker on success
        UrlCache.CacheResult halfOpenResult1 = cache.get("abc");
        UrlCache.CacheResult halfOpenResult2 = cache.get("abc");
        UrlCache.CacheResult halfOpenResult3 = cache.get("abc");
        assertThat(halfOpenResult3).isInstanceOf(UrlCache.Hit.class);
        
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
