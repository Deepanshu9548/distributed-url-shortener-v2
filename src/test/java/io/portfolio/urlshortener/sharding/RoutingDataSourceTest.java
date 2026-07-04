package io.portfolio.urlshortener.sharding;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routes real JDBC connections through {@link RoutingDataSource}: two H2
 * in-memory databases stand in for shard1's primary and replica, each holding
 * a marker row naming itself; the test asserts which database answered.
 */
class RoutingDataSourceTest {

    private HashRingShardRouter router;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DataSource primary = h2("routing_ds_primary", "primary");
        DataSource replica = h2("routing_ds_replica", "replica");
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("shard1:PRIMARY", primary);
        targets.put("shard1:REPLICA", replica);
        RoutingDataSource routing = new RoutingDataSource(targets, primary);
        routing.afterPropertiesSet();
        // single-shard ring: every key routes to shard1
        router = new HashRingShardRouter(
                new ConsistentHashRing(List.of("shard1"), 150), new SimpleMeterRegistry());
        jdbc = new JdbcTemplate(routing);
    }

    @Test
    void executeReadRoutesConnectionsToTheReplica() {
        String origin = router.executeRead("abc123",
                () -> jdbc.queryForObject("SELECT name FROM origin", String.class));
        assertThat(origin).isEqualTo("replica");
    }

    @Test
    void executeWriteRoutesConnectionsToThePrimary() {
        String origin = router.executeWrite("abc123",
                () -> jdbc.queryForObject("SELECT name FROM origin", String.class));
        assertThat(origin).isEqualTo("primary");
    }

    @Test
    void accessOutsideAnyRoutingContextUsesTheDefaultPrimary() {
        // e.g. boot-time schema validation — no ShardContext set
        assertThat(ShardContext.current()).isEmpty();
        String origin = jdbc.queryForObject("SELECT name FROM origin", String.class);
        assertThat(origin).isEqualTo("primary");
    }

    static DataSource h2(String dbName, String marker) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        JdbcTemplate template = new JdbcTemplate(ds);
        template.execute("DROP TABLE IF EXISTS origin");
        template.execute("CREATE TABLE origin(name VARCHAR(16))");
        template.update("INSERT INTO origin(name) VALUES (?)", marker);
        return ds;
    }
}
