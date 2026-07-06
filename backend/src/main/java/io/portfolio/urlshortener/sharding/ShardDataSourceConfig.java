package io.portfolio.urlshortener.sharding;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Active only when {@code app.sharding.enabled=true}. Builds, per configured
 * shard, a Hikari pool for the primary and one for the replica, runs the
 * per-shard Flyway migrations ({@code classpath:db/migration/shard})
 * against every <b>primary</b> (replicas receive schema via streaming
 * replication, ADR-002), and exposes the {@link RoutingDataSource} as the
 * {@code @Primary} DataSource that JPA binds to.
 *
 * <p>Sizing/timeouts: connection-timeout 1s ("every external call has a
 * timeout"). Primary pools fail fast at boot (a dead primary is fatal —
 * Flyway must run); replica pools initialize lazily
 * ({@code initializationFailTimeout=-1}) because a replica may still be
 * running {@code pg_basebackup} when the app starts — reads simply fall back
 * to the primary until it catches up.
 *
 * <p>The control DB (users/auth, Track C, ADR-010) is NOT routed through the
 * ring and is not configured here; it needs its own explicitly qualified
 * datasource beans.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.sharding", name = "enabled", havingValue = "true")
public class ShardDataSourceConfig {

    static final String SHARD_MIGRATION_LOCATION = "classpath:db/migration/shard";
    static final long CONNECTION_TIMEOUT_MS = 1000;

    private static final Logger log = LoggerFactory.getLogger(ShardDataSourceConfig.class);

    @Bean
    @Primary
    public DataSource routingDataSource(ShardProperties properties) {
        if (properties.shards().isEmpty()) {
            throw new IllegalStateException("app.sharding.enabled=true but app.sharding.shards is empty");
        }
        Map<Object, Object> targets = new LinkedHashMap<>();
        DataSource defaultTarget = null;
        for (ShardProperties.Shard shard : properties.shards()) {
            HikariDataSource primary = pool(shard.name() + "-primary", shard.primary(), true);
            migrate(shard.name(), primary);
            HikariDataSource replica = pool(shard.name() + "-replica", shard.replica(), false);
            targets.put(shard.name() + ":" + ShardContext.Mode.PRIMARY, primary);
            targets.put(shard.name() + ":" + ShardContext.Mode.REPLICA, replica);
            if (defaultTarget == null) {
                defaultTarget = primary; // first shard's primary = boot-time/default target
            }
        }
        log.info("sharding enabled: {} shards, {} vnodes each", properties.shards().size(), properties.vnodes());
        return new RoutingDataSource(targets, defaultTarget);
    }

    private static HikariDataSource pool(String name, ShardProperties.Node node, boolean failFastAtBoot) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(name);
        ds.setJdbcUrl(node.jdbcUrl());
        ds.setUsername(node.username());
        ds.setPassword(node.password());
        ds.setMaximumPoolSize(node.poolSize());
        ds.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        
        String schema = name.split("-")[0];
        ds.setSchema(schema);
        ds.setConnectionInitSql("SET search_path TO " + schema);

        if (!failFastAtBoot) {
            ds.setInitializationFailTimeout(-1);
        }
        return ds;
    }

    private static void migrate(String shardName, DataSource primary) {
        log.info("running shard migrations on {} primary", shardName);
        Flyway.configure()
                .dataSource(primary)
                .locations(SHARD_MIGRATION_LOCATION)
                .schemas(shardName)
                .createSchemas(true)
                .load()
                .migrate();
    }
}
