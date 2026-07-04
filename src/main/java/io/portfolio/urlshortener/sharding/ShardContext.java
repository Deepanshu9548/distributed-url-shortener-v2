package io.portfolio.urlshortener.sharding;

import java.util.Optional;

/**
 * ThreadLocal routing context: which shard, and PRIMARY or REPLICA. Set and
 * cleared exclusively by {@link HashRingShardRouter} (package-private
 * mutators, always cleared in {@code finally} — the ShardRouter contract
 * certifies the router stays usable after a supplier throws). Read by
 * {@link RoutingDataSource} to pick the JDBC target, and publicly readable
 * for metrics/tests.
 */
public final class ShardContext {

    public enum Mode { PRIMARY, REPLICA }

    /** Immutable snapshot of the current route. */
    public record Route(String shard, Mode mode) {
        String lookupKey() {
            return shard + ":" + mode;
        }
    }

    private static final ThreadLocal<Route> CURRENT = new ThreadLocal<>();

    private ShardContext() {
    }

    static void set(String shard, Mode mode) {
        CURRENT.set(new Route(shard, mode));
    }

    static void clear() {
        CURRENT.remove();
    }

    /** Current route, if a ShardRouter operation is executing on this thread. */
    public static Optional<Route> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Lookup key for {@link RoutingDataSource} ({@code "<shard>:PRIMARY|REPLICA"}), or null. */
    static String currentLookupKey() {
        Route route = CURRENT.get();
        return route == null ? null : route.lookupKey();
    }
}
