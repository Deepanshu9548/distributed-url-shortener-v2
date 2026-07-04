package io.portfolio.urlshortener.sharding;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replica-fallback, metric, and context-hygiene behavior of
 * {@link HashRingShardRouter} — all against in-memory H2 / failing
 * datasources, no Docker.
 */
class HashRingShardRouterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final HashRingShardRouter router = new HashRingShardRouter(
            new ConsistentHashRing(List.of("shard1"), 150), registry);

    @Test
    void readFallsBackToPrimaryExactlyOnceWhenReplicaConnectionFails() {
        DataSource primary = RoutingDataSourceTest.h2("router_fb_primary", "primary");
        JdbcTemplate jdbc = jdbcOver(primary, new ThrowingDataSource());

        AtomicInteger attempts = new AtomicInteger();
        List<ShardContext.Route> routesSeen = new ArrayList<>();
        String origin = router.executeRead("abc123", () -> {
            attempts.incrementAndGet();
            routesSeen.add(ShardContext.current().orElseThrow());
            return jdbc.queryForObject("SELECT name FROM origin", String.class);
        });

        assertThat(origin).isEqualTo("primary");
        assertThat(attempts).hasValue(2); // replica attempt + exactly one fallback
        assertThat(routesSeen).containsExactly(
                new ShardContext.Route("shard1", ShardContext.Mode.REPLICA),
                new ShardContext.Route("shard1", ShardContext.Mode.PRIMARY));
        assertThat(count("replica_fallback")).isEqualTo(1.0);
        assertThat(count("replica")).isZero();
        assertThat(ShardContext.current()).isEmpty();
    }

    @Test
    void businessExceptionInReplicaModePropagatesWithoutFallback() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> router.executeRead("abc123", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("business boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("business boom");

        assertThat(calls).hasValue(1); // never retried
        assertThat(count("replica_fallback")).isZero();
        assertThat(ShardContext.current()).isEmpty();
    }

    @Test
    void connectionFailureOnBothReplicaAndPrimaryPropagatesAfterSingleFallback() {
        JdbcTemplate jdbc = jdbcOver(new ThrowingDataSource(), new ThrowingDataSource());

        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> router.executeRead("abc123", () -> {
            attempts.incrementAndGet();
            return jdbc.queryForObject("SELECT name FROM origin", String.class);
        })).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(attempts).hasValue(2); // no second fallback, no loop
        assertThat(ShardContext.current()).isEmpty();
    }

    @Test
    void successfulReadOnReplicaCountsReplicaOutcome() {
        router.executeRead("abc123", () -> "ok");
        assertThat(count("replica")).isEqualTo(1.0);
        assertThat(count("replica_fallback")).isZero();
    }

    @Test
    void writeCountsPrimaryOutcomeAndRunsInPrimaryMode() {
        List<ShardContext.Route> routesSeen = new ArrayList<>();
        router.executeWrite("abc123", () -> {
            routesSeen.add(ShardContext.current().orElseThrow());
            return "ok";
        });
        assertThat(routesSeen).containsExactly(
                new ShardContext.Route("shard1", ShardContext.Mode.PRIMARY));
        assertThat(count("primary")).isEqualTo(1.0);
        assertThat(ShardContext.current()).isEmpty();
    }

    @Test
    void contextIsClearedAfterEveryOutcomeIncludingWriteFailure() {
        assertThatThrownBy(() -> router.executeWrite("abc123", () -> {
            throw new IllegalStateException("write boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(ShardContext.current()).isEmpty();

        // router remains fully usable afterwards
        assertThat(router.<String>executeRead("abc123", () -> "still ok")).isEqualTo("still ok");
        assertThat(ShardContext.current()).isEmpty();
    }

    private double count(String outcome) {
        Counter counter = registry.find(HashRingShardRouter.ROUTE_METRIC)
                .tags("shard", "shard1", "outcome", outcome)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static JdbcTemplate jdbcOver(DataSource primary, DataSource replica) {
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("shard1:PRIMARY", primary);
        targets.put("shard1:REPLICA", replica);
        RoutingDataSource routing = new RoutingDataSource(targets, primary);
        routing.afterPropertiesSet();
        return new JdbcTemplate(routing);
    }

    /** Simulates a dead replica: every connection acquisition fails. */
    private static final class ThrowingDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("replica down");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("replica down");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
