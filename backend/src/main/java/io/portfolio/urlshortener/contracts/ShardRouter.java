package io.portfolio.urlshortener.contracts;

import java.util.function.Supplier;

/**
 * CONTRACT — frozen post-M0. Routes an operation to the shard owning {@code key}.
 *
 * <p>The routing key is always the short code (never user id): the redirect hot
 * path must resolve its shard with zero lookups (ADR-002).
 *
 * <p>Implementations MUST guarantee:
 * <ul>
 *   <li>Same key → same shard, always (stable hashing).</li>
 *   <li>{@code executeWrite} runs against the shard primary.</li>
 *   <li>{@code executeRead} prefers the shard replica and falls back to the
 *       primary exactly once on replica failure.</li>
 *   <li>Routing context is cleared after the supplier returns (finally).</li>
 *   <li>No operation ever spans two shards in one transaction.</li>
 * </ul>
 */
public interface ShardRouter {

    <T> T executeWrite(String key, Supplier<T> operation);

    <T> T executeRead(String key, Supplier<T> operation);

    /** Name of the shard that owns {@code key} (for metrics/tests). */
    String shardFor(String key);
}
