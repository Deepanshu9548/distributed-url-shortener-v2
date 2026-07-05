package io.portfolio.urlshortener.contracts;

/**
 * CONTRACT — frozen post-M0. Fire-and-forget event publication.
 *
 * <p>Implementations MUST NEVER block or throw on the caller thread: the
 * redirect path calls {@link #publishClick} inline and its latency budget does
 * not include messaging (ADR-006). Failures are counted, not propagated.
 */
public interface EventPublisher {

    void publishClick(ClickEvent event);

    void publishLinkEvent(LinkEvent event);
}
