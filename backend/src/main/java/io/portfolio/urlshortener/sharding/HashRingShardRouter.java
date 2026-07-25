package io.portfolio.urlshortener.sharding;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.portfolio.urlshortener.contracts.ShardRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.function.Supplier;

/**
 * Real {@link ShardRouter} (ADR-002): consistent-hash ring picks the owning
 * shard, {@link ShardContext} steers {@link RoutingDataSource} to that
 * shard's primary (writes) or replica (reads).
 *
 * <p><b>Replica fallback:</b> {@code executeRead} retries the supplier
 * <b>exactly once</b> against the primary when the replica attempt fails
 * with a connection-acquisition failure
 * ({@link DataAccessResourceFailureException} — which covers
 * {@code CannotGetJdbcConnectionException} — or
 * {@link CannotCreateTransactionException}, thrown when a transactional
 * repository call cannot obtain a connection). Any other exception is a
 * business error and propagates untouched, with no fallback.
 *
 * <p>The routing context is cleared in {@code finally}, so the router stays
 * usable after a supplier throws (certified by ShardRouterContractTest).
 *
 * <p><b>Bean availability:</b> {@code @ConditionalOnProperty} keeps this bean
 * out of the context when {@code app.sharding.enabled=false} (local dev,
 * unit-test slices) — the {@code SingleShardRouter} stub remains the router
 * there. When sharding is on, {@code @Primary} makes this bean win injection
 * over the stub without touching contracts territory (same pattern as
 * {@code RedisUrlCache} over {@code NoopCache}).
 *
 * <p>Metric: {@code shard.route.total} tagged {@code shard=<name>},
 * {@code outcome=primary|replica|replica_fallback} (allowed labels only —
 * never key values). Counted once per completed routed operation.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "app.sharding", name = "enabled", havingValue = "true")
public class HashRingShardRouter implements ShardRouter {

    static final String ROUTE_METRIC = "shard.route.total";

    private final ConsistentHashRing ring;
    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    public HashRingShardRouter(ShardProperties properties, MeterRegistry meterRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        this(new ConsistentHashRing(properties.shardNames(), properties.vnodes()), meterRegistry, circuitBreakerRegistry);
    }

    HashRingShardRouter(ConsistentHashRing ring, MeterRegistry meterRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.ring = ring;
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public <T> T executeWrite(String key, Supplier<T> operation) {
        String shard = ring.shardFor(key);
        ShardContext.set(shard, ShardContext.Mode.PRIMARY);
        try {
            CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("shard-" + shard);
            T result = breaker.executeSupplier(operation);
            count(shard, "primary");
            return result;
        } catch (CallNotPermittedException e) {
            countFallback(shard);
            throw new ShardUnavailableException(shard, e);
        } finally {
            ShardContext.clear();
        }
    }

    @Override
    public <T> T executeRead(String key, Supplier<T> operation) {
        String shard = ring.shardFor(key);
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("shard-" + shard);
        try {
            ShardContext.set(shard, ShardContext.Mode.REPLICA);
            try {
                T result = breaker.executeSupplier(operation);
                count(shard, "replica");
                return result;
            } catch (RuntimeException e) {
                if (e instanceof CallNotPermittedException) {
                    throw e; // Fail fast without falling back to primary on the same broken shard
                }
                if (!isConnectionFailure(e)) {
                    throw e; // business exception — no fallback, propagate untouched
                }
            }
            // replica unreachable → exactly one retry against the primary
            ShardContext.set(shard, ShardContext.Mode.PRIMARY);
            T result = breaker.executeSupplier(operation);
            count(shard, "replica_fallback");
            return result;
        } catch (CallNotPermittedException e) {
            countFallback(shard);
            throw new ShardUnavailableException(shard, e);
        } finally {
            ShardContext.clear();
        }
    }

    @Override
    public String shardFor(String key) {
        return ring.shardFor(key);
    }

    private static boolean isConnectionFailure(RuntimeException e) {
        return e instanceof DataAccessResourceFailureException
                || e instanceof CannotCreateTransactionException;
    }

    private void count(String shard, String outcome) {
        meterRegistry.counter(ROUTE_METRIC, "shard", shard, "outcome", outcome).increment();
    }

    private void countFallback(String shard) {
        meterRegistry.counter("resilience.fallback.total", "breaker", "shard-" + shard, "outcome", "fallback").increment();
    }
}
