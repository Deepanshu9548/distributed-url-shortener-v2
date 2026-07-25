package io.portfolio.urlshortener.sharding;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * Per-request shard routing: resolves the JDBC target from the
 * {@link ShardContext} ThreadLocal using {@code "<shard>:PRIMARY|REPLICA"}
 * lookup keys. When no context is set (application boot, Hibernate schema
 * validation, anything outside a {@code ShardRouter} scope) the default
 * target — shard1's primary — is used, so the app always boots cleanly.
 */
public class RoutingDataSource extends AbstractRoutingDataSource implements Closeable {

    private final List<Object> targets;

    public RoutingDataSource(Map<Object, Object> targetDataSources, DataSource defaultTarget) {
        this.targets = List.copyOf(targetDataSources.values());
        setTargetDataSources(targetDataSources);
        setDefaultTargetDataSource(defaultTarget);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return ShardContext.currentLookupKey();
    }

    /**
     * Closes every closeable target pool (Hikari) on context shutdown —
     * Spring picks this up as the bean's inferred destroy method.
     */
    @Override
    public void close() {
        for (Object target : targets) {
            if (target instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    logger.warn("failed closing shard datasource: " + e);
                }
            }
        }
    }
}
