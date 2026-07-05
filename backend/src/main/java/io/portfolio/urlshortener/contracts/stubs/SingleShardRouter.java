package io.portfolio.urlshortener.contracts.stubs;

import io.portfolio.urlshortener.contracts.ShardRouter;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * STUB — routes everything to a single datasource ("shard1"). Lets the core
 * track build and test against one DB before the real consistent-hash router
 * (Track B) lands. Replaced at M2: the real router is @Primary and wins injection.
 */
@Component
public class SingleShardRouter implements ShardRouter {

    @Override
    public <T> T executeWrite(String key, Supplier<T> operation) {
        return operation.get();
    }

    @Override
    public <T> T executeRead(String key, Supplier<T> operation) {
        return operation.get();
    }

    @Override
    public String shardFor(String key) {
        return "shard1";
    }
}
