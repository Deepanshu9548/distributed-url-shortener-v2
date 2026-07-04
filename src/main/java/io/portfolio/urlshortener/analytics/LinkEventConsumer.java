package io.portfolio.urlshortener.analytics;

import io.portfolio.urlshortener.auth.LinkIndexRepository;
import io.portfolio.urlshortener.auth.UserLink;
import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.UrlCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LinkEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(LinkEventConsumer.class);

    private final RawLinkEventRepository rawLinkEventRepository;
    private final LinkIndexRepository linkIndexRepository;
    private final UrlCache urlCache;

    public LinkEventConsumer(RawLinkEventRepository rawLinkEventRepository,
                             LinkIndexRepository linkIndexRepository,
                             UrlCache urlCache) {
        this.rawLinkEventRepository = rawLinkEventRepository;
        this.linkIndexRepository = linkIndexRepository;
        this.urlCache = urlCache;
    }

    @KafkaListener(topics = "link-events", groupId = "link-index", autoStartup = "${app.kafka.enabled:false}")
    @Transactional("controlTransactionManager")
    public void consume(LinkEvent event) {
        log.debug("Consumed link event: {}", event.eventId());
        int rows = rawLinkEventRepository.insertIgnore(event.eventId(), event.timestamp());
        if (rows == 0) {
            log.debug("Skipped duplicate link event: {}", event.eventId());
            return;
        }

        switch (event.type()) {
            case CREATED -> linkIndexRepository.save(new UserLink(event.shortCode(), event.userId(), event.timestamp()));
            case DELETED -> {
                linkIndexRepository.deleteById(event.shortCode());
                urlCache.evict(event.shortCode());
            }
            case UPDATED -> urlCache.evict(event.shortCode());
        }
    }
}
