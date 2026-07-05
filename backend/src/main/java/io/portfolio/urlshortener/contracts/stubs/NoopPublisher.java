package io.portfolio.urlshortener.contracts.stubs;

import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import org.springframework.stereotype.Component;

/**
 * STUB — drops events. Equivalent to "Kafka permanently down with an empty
 * buffer", the exact degradation mode the real publisher must survive.
 */
@Component
public class NoopPublisher implements EventPublisher {

    @Override
    public void publishClick(ClickEvent event) {
        // no-op
    }

    @Override
    public void publishLinkEvent(LinkEvent event) {
        // no-op
    }
}
