package io.portfolio.urlshortener.events;

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
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void publishClick(ClickEvent event) {
        kafkaTemplate.send("click-events", event.shortCode(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish click event: {}", event.eventId(), ex);
                    meterRegistry.counter("events.publish.total", "outcome", "error").increment();
                } else {
                    meterRegistry.counter("events.publish.total", "outcome", "ok").increment();
                }
            });
    }

    @Override
    public void publishLinkEvent(LinkEvent event) {
        kafkaTemplate.send("link-events", event.shortCode(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish link event: {}", event.eventId(), ex);
                    meterRegistry.counter("events.publish.total", "outcome", "error").increment();
                } else {
                    meterRegistry.counter("events.publish.total", "outcome", "ok").increment();
                }
            });
    }
}
