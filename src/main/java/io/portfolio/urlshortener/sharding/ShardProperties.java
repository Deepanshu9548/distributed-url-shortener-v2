package io.portfolio.urlshortener.sharding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Binds the {@code app.sharding} section of application.yml. Discovered via
 * {@code @ConfigurationPropertiesScan} (constructor binding — plain records,
 * no Lombok).
 *
 * <p>{@code enabled=false} (the local default) leaves the whole sharding
 * subsystem dormant: no shard datasources are built and the
 * {@code SingleShardRouter} stub remains the {@code ShardRouter} bean.
 */
@ConfigurationProperties(prefix = "app.sharding")
public record ShardProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("150") int vnodes,
        List<Shard> shards) {

    public ShardProperties {
        shards = shards == null ? List.of() : List.copyOf(shards);
    }

    /** Ring member names, in declaration order (shard1 first = default target). */
    public List<String> shardNames() {
        return shards.stream().map(Shard::name).toList();
    }

    /** One physical shard: a Postgres primary plus its streaming replica. */
    public record Shard(String name, Node primary, Node replica) {
    }

    /** One Postgres node (primary or replica) and its Hikari pool sizing. */
    public record Node(String jdbcUrl,
                       String username,
                       String password,
                       @DefaultValue("10") int poolSize) {
    }
}
