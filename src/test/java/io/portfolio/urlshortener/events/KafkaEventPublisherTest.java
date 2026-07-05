package io.portfolio.urlshortener.events;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.LinkEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private SimpleMeterRegistry meterRegistry;
    private KafkaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new KafkaEventPublisher(kafkaTemplate, meterRegistry, io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishClick_success() {
        when(kafkaTemplate.send(eq("click-events"), anyString(), any(ClickEvent.class)))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));

        ClickEvent event = ClickEvent.of("short1", "ref", "ua", "req1");
        publisher.publishClick(event);

        verify(kafkaTemplate).send("click-events", "short1", event);
        assertEquals(1, meterRegistry.counter("events.publish.total", "outcome", "ok").count());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishClick_error() {
        CompletableFuture future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(eq("click-events"), anyString(), any(ClickEvent.class)))
                .thenReturn(future);

        ClickEvent event = ClickEvent.of("short2", "ref", "ua", "req2");
        publisher.publishClick(event);

        assertEquals(1, meterRegistry.counter("events.publish.total", "outcome", "error").count());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishLinkEvent_success() {
        when(kafkaTemplate.send(eq("link-events"), anyString(), any(LinkEvent.class)))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));

        LinkEvent event = LinkEvent.of(LinkEvent.Type.CREATED, "short3", 123L, "req3");
        publisher.publishLinkEvent(event);

        verify(kafkaTemplate).send("link-events", "short3", event);
        assertEquals(1, meterRegistry.counter("events.publish.total", "outcome", "ok").count());
    }
}
