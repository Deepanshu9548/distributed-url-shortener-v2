package io.portfolio.urlshortener.analytics;

import io.portfolio.urlshortener.contracts.ClickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClickConsumer {
    private static final Logger log = LoggerFactory.getLogger(ClickConsumer.class);

    private final RawClickEventRepository rawClickEventRepository;
    private final LinkStatsRepository linkStatsRepository;

    public ClickConsumer(RawClickEventRepository rawClickEventRepository, LinkStatsRepository linkStatsRepository) {
        this.rawClickEventRepository = rawClickEventRepository;
        this.linkStatsRepository = linkStatsRepository;
    }

    @KafkaListener(topics = "click-events", groupId = "analytics", autoStartup = "${app.kafka.enabled:false}")
    @Transactional("controlTransactionManager")
    public void consume(ClickEvent event) {
        log.debug("Consumed click event: {}", event.eventId());
        int rows = rawClickEventRepository.insertIgnore(event.eventId(), event.timestamp());
        if (rows > 0) {
            linkStatsRepository.incrementClickCount(event.shortCode(), event.timestamp(), event.referrer());
        } else {
            log.debug("Skipped duplicate click event: {}", event.eventId());
        }
    }
}
