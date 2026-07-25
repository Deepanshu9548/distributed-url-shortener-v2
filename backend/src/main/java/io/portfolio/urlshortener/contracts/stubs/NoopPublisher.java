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
    private final io.portfolio.urlshortener.analytics.ClickConsumer clickConsumer;
    private final io.portfolio.urlshortener.analytics.LinkEventConsumer linkEventConsumer;

    public NoopPublisher(
            org.springframework.beans.factory.ObjectProvider<io.portfolio.urlshortener.analytics.ClickConsumer> clickConsumer,
            org.springframework.beans.factory.ObjectProvider<io.portfolio.urlshortener.analytics.LinkEventConsumer> linkEventConsumer) {
        this.clickConsumer = clickConsumer.getIfAvailable();
        this.linkEventConsumer = linkEventConsumer.getIfAvailable();
    }

    @Override
    public void publishClick(ClickEvent event) {
        if (clickConsumer != null) {
            clickConsumer.consume(event);
        }
    }

    @Override
    public void publishLinkEvent(LinkEvent event) {
        if (linkEventConsumer != null) {
            linkEventConsumer.consume(event);
        }
    }
}
