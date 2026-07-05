package io.portfolio.urlshortener.events;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    static final String BREAKER_NAME = "kafka-publisher";
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(BREAKER_NAME);
    }

    @Override
    public void publishClick(ClickEvent event) {
        try {
            circuitBreaker.executeSupplier(() -> kafkaTemplate.send("click-events", event.shortCode(), event))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish click event: {}", event.eventId(), ex);
                        meterRegistry.counter("events.publish.total", "outcome", "error").increment();
                    } else {
                        meterRegistry.counter("events.publish.total", "outcome", "ok").increment();
                    }
                });
        } catch (CallNotPermittedException e) {
            countFallback();
            meterRegistry.counter("events.publish.total", "outcome", "error").increment();
            log.warn("Kafka circuit breaker open, dropping click event: {}", event.eventId());
        } catch (Exception e) {
            countFallback();
            meterRegistry.counter("events.publish.total", "outcome", "error").increment();
            log.warn("Kafka publisher degraded, dropping click event: {}", event.eventId(), e);
        }
    }

    @Override
    public void publishLinkEvent(LinkEvent event) {
        try {
            circuitBreaker.executeSupplier(() -> kafkaTemplate.send("link-events", event.shortCode(), event))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish link event: {}", event.eventId(), ex);
                        meterRegistry.counter("events.publish.total", "outcome", "error").increment();
                    } else {
                        meterRegistry.counter("events.publish.total", "outcome", "ok").increment();
                    }
                });
        } catch (CallNotPermittedException e) {
            countFallback();
            meterRegistry.counter("events.publish.total", "outcome", "error").increment();
            log.warn("Kafka circuit breaker open, dropping link event: {}", event.eventId());
        } catch (Exception e) {
            countFallback();
            meterRegistry.counter("events.publish.total", "outcome", "error").increment();
            log.warn("Kafka publisher degraded, dropping link event: {}", event.eventId(), e);
        }
    }

    private void countFallback() {
        meterRegistry.counter("resilience.fallback.total", "breaker", BREAKER_NAME, "outcome", "fallback").increment();
    }
}
