package io.portfolio.urlshortener.sharding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Certifies the yml shape of `app.sharding` binds (kebab-case, defaults). */
class ShardPropertiesTest {

    @Test
    void bindsFullShapeWithDefaults() {
        Map<String, String> source = new HashMap<>();
        source.put("app.sharding.enabled", "true");
        source.put("app.sharding.shards[0].name", "shard1");
        source.put("app.sharding.shards[0].primary.jdbc-url", "jdbc:postgresql://p1:5432/shortener");
        source.put("app.sharding.shards[0].primary.username", "shortener");
        source.put("app.sharding.shards[0].primary.password", "secret");
        source.put("app.sharding.shards[0].primary.pool-size", "5");
        source.put("app.sharding.shards[0].replica.jdbc-url", "jdbc:postgresql://r1:5432/shortener");
        source.put("app.sharding.shards[0].replica.username", "shortener");
        source.put("app.sharding.shards[0].replica.password", "secret");

        ShardProperties props = new Binder(new MapConfigurationPropertySource(source))
                .bind("app.sharding", ShardProperties.class)
                .get();

        assertThat(props.enabled()).isTrue();
        assertThat(props.vnodes()).isEqualTo(150); // default
        assertThat(props.shardNames()).containsExactly("shard1");
        ShardProperties.Shard shard = props.shards().get(0);
        assertThat(shard.primary().jdbcUrl()).isEqualTo("jdbc:postgresql://p1:5432/shortener");
        assertThat(shard.primary().poolSize()).isEqualTo(5);
        assertThat(shard.replica().poolSize()).isEqualTo(10); // default
    }

    @Test
    void disabledConfigBindsWithEmptyShardList() {
        Map<String, String> source = Map.of("app.sharding.enabled", "false");

        ShardProperties props = new Binder(new MapConfigurationPropertySource(source))
                .bind("app.sharding", ShardProperties.class)
                .get();

        assertThat(props.enabled()).isFalse();
        assertThat(props.vnodes()).isEqualTo(150);
        assertThat(props.shards()).isEmpty();
        assertThat(props.shardNames()).isEmpty();
    }
}
