package io.portfolio.urlshortener.sharding;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Immutable consistent-hash ring (ADR-002): {@code murmur3_32_fixed} over the
 * routing key, {@value #DEFAULT_VNODES} virtual nodes per physical shard by
 * default. Vnode positions hash {@code "<shardName>#<vnodeIndex>"}; a key is
 * owned by the first vnode at or clockwise after its hash (ceiling entry,
 * wrapping to the first entry).
 *
 * <p>Keys are arbitrary strings — usually short codes, but Track A also routes
 * idempotency keys through the same ring (same key → same shard, so client
 * retries land where the first attempt wrote).
 *
 * <p>{@code murmur3_32_fixed} is stable across JVM restarts and versions
 * (unlike the deprecated {@code murmur3_32}), so assignments are deterministic
 * for the life of the shard topology. Adding shard N+1 remaps ~1/(N+1) of the
 * keys, and every remapped key moves to the new shard only.
 */
public final class ConsistentHashRing {

    public static final int DEFAULT_VNODES = 150;

    private static final HashFunction HASH = Hashing.murmur3_32_fixed();

    private final NavigableMap<Integer, String> ring = new TreeMap<>();
    private final List<String> shards;
    private final int vnodesPerShard;

    public ConsistentHashRing(List<String> shardNames, int vnodesPerShard) {
        if (shardNames == null || shardNames.isEmpty()) {
            throw new IllegalArgumentException("consistent-hash ring needs at least one shard");
        }
        if (vnodesPerShard <= 0) {
            throw new IllegalArgumentException("vnodesPerShard must be positive, was " + vnodesPerShard);
        }
        this.shards = List.copyOf(shardNames);
        this.vnodesPerShard = vnodesPerShard;
        for (String shard : this.shards) {
            for (int vnode = 0; vnode < vnodesPerShard; vnode++) {
                ring.put(hash(shard + "#" + vnode), shard);
            }
        }
    }

    /** Name of the shard owning {@code key} — stable for a fixed topology. */
    public String shardFor(String key) {
        Map.Entry<Integer, String> owner = ring.ceilingEntry(hash(key));
        return (owner != null ? owner : ring.firstEntry()).getValue();
    }

    public List<String> shards() {
        return shards;
    }

    public int vnodesPerShard() {
        return vnodesPerShard;
    }

    private static int hash(String value) {
        return HASH.hashString(value, StandardCharsets.UTF_8).asInt();
    }
}
