package io.portfolio.urlshortener.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.events.KafkaEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("chaos")
public class KafkaPublisherChaosTest {

    private CircuitBreakerRegistry registry;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaEventPublisher publisher;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker breaker = registry.circuitBreaker("kafka-publisher", io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .slidingWindowSize(20)
            .failureRateThreshold(50.0f)
            .build());
            
        kafkaTemplate = mock(KafkaTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new KafkaEventPublisher(kafkaTemplate, meterRegistry, registry);
    }

    @Test
    void testKafkaPublisherCircuitBreaker() {
        CircuitBreaker breaker = registry.circuitBreaker("kafka-publisher");

        // Force failures
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("Kafka down"));

        ClickEvent event = ClickEvent.of("abc", "referrer", "userAgent", "req123");
        for (int i = 0; i < 20; i++) {
            // Exception does not propagate to caller (fire and forget)
            publisher.publishClick(event);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        reset(kafkaTemplate);
        
        // Next call short-circuits
        publisher.publishClick(event);
        
        verifyNoInteractions(kafkaTemplate);
        
        // Assert metric
        double errorCount = meterRegistry.get("events.publish.total")
                .tag("outcome", "error")
                .counter().count();
        assertThat(errorCount).isGreaterThan(0);
        
        double fallbackCount = meterRegistry.get("resilience.fallback.total")
                .tag("breaker", "kafka-publisher")
                .tag("outcome", "fallback")
                .counter().count();
        assertThat(fallbackCount).isGreaterThan(0);
    }
}
