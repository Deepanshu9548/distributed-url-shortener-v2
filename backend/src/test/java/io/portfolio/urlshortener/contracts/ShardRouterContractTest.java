package io.portfolio.urlshortener.contracts;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CONTRACT TEST — every ShardRouter implementation must pass.
 */
@Tag("contract")
public abstract class ShardRouterContractTest {

    protected abstract ShardRouter router();

    @Test
    void sameKeyAlwaysSameShard() {
        String shard = router().shardFor("abc123");
        for (int i = 0; i < 1000; i++) {
            assertThat(router().shardFor("abc123")).isEqualTo(shard);
        }
    }

    @Test
    void executeWriteReturnsSupplierResult() {
        assertThat(router().<String>executeWrite("k", () -> "written")).isEqualTo("written");
    }

    @Test
    void executeReadReturnsSupplierResult() {
        assertThat(router().<Integer>executeRead("k", () -> 42)).isEqualTo(42);
    }

    @Test
    void supplierExceptionPropagatesAndContextIsCleared() {
        assertThatThrownBy(() -> router().executeWrite("k", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        // Router must be usable again after a failure (context cleared in finally)
        assertThat(router().<String>executeRead("k", () -> "ok")).isEqualTo("ok");
    }
}
