package io.portfolio.urlshortener.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    public NewTopic clickEventsTopic() {
        return TopicBuilder.name("click-events")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic linkEventsTopic() {
        return TopicBuilder.name("link-events")
                .partitions(6)
                .replicas(1)
                .build();
    }
}
