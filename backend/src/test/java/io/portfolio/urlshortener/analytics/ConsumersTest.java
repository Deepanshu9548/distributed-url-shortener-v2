package io.portfolio.urlshortener.analytics;

import io.portfolio.urlshortener.auth.LinkIndexRepository;
import io.portfolio.urlshortener.auth.UserLink;
import io.portfolio.urlshortener.contracts.ClickEvent;
import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.UrlCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConsumersTest {

    @Mock private RawClickEventRepository rawClickRepo;
    @Mock private LinkStatsRepository statsRepo;
    @Mock private RawLinkEventRepository rawLinkRepo;
    @Mock private LinkIndexRepository linkIndexRepo;
    @Mock private UrlCache urlCache;

    private ClickConsumer clickConsumer;
    private LinkEventConsumer linkConsumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clickConsumer = new ClickConsumer(rawClickRepo, statsRepo);
        linkConsumer = new LinkEventConsumer(rawLinkRepo, linkIndexRepo, urlCache);
    }

    @Test
    void clickConsumer_insertsAndIncrements() {
        when(rawClickRepo.insertIgnore(any(), any())).thenReturn(1);
        ClickEvent event = ClickEvent.of("short1", "ref", "ua", "req1");

        clickConsumer.consume(event);

        verify(rawClickRepo).insertIgnore(event.eventId(), event.timestamp());
        verify(statsRepo).incrementClickCount("short1", event.timestamp(), "ref");
    }

    @Test
    void clickConsumer_duplicateSkipped() {
        when(rawClickRepo.insertIgnore(any(), any())).thenReturn(0);
        ClickEvent event = ClickEvent.of("short1", "ref", "ua", "req1");

        clickConsumer.consume(event);

        verify(statsRepo, never()).incrementClickCount(any(), any(), any());
    }

    @Test
    void linkConsumer_created() {
        when(rawLinkRepo.insertIgnore(any(), any())).thenReturn(1);
        LinkEvent event = LinkEvent.of(LinkEvent.Type.CREATED, "short2", 123L, "req2");

        linkConsumer.consume(event);

        verify(linkIndexRepo).save(any(UserLink.class));
        verify(urlCache, never()).evict(any());
    }

    @Test
    void linkConsumer_deleted() {
        when(rawLinkRepo.insertIgnore(any(), any())).thenReturn(1);
        LinkEvent event = LinkEvent.of(LinkEvent.Type.DELETED, "short3", 123L, "req3");

        linkConsumer.consume(event);

        verify(linkIndexRepo).deleteById("short3");
        verify(urlCache).evict("short3");
    }
}
