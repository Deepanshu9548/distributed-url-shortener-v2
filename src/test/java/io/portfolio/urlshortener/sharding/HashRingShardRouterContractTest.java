package io.portfolio.urlshortener.sharding;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.contracts.ShardRouterContractTest;

import java.util.List;

/**
 * Certifies {@link HashRingShardRouter} against the frozen ShardRouter
 * contract (@Tag("contract") inherited). Pure unit: the contract suppliers
 * are lambdas, so no datasource is involved — only ring + context semantics
 * are exercised.
 */
class HashRingShardRouterContractTest extends ShardRouterContractTest {

    private final HashRingShardRouter router = new HashRingShardRouter(
            new ConsistentHashRing(List.of("shard1", "shard2"), 150),
            new SimpleMeterRegistry());

    @Override
    protected ShardRouter router() {
        return router;
    }
}
