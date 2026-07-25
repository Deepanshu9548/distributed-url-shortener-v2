package io.portfolio.urlshortener.sharding;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsistentHashRingTest {

    private static final int KEYS = 100_000;
    private static final int VNODES = 150;

    @Test
    void sameKeySameShardAcrossFreshRingInstances() {
        ConsistentHashRing first = new ConsistentHashRing(List.of("shard1", "shard2"), VNODES);
        ConsistentHashRing second = new ConsistentHashRing(List.of("shard1", "shard2"), VNODES);
        Random random = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            String key = "key-" + random.nextLong();
            assertThat(second.shardFor(key)).isEqualTo(first.shardFor(key));
        }
    }

    @Test
    void distributionOverTwoShardsIsRoughlyBalanced() {
        ConsistentHashRing ring = new ConsistentHashRing(List.of("shard1", "shard2"), VNODES);
        Map<String, Integer> counts = new HashMap<>();
        Random random = new Random(7);
        for (int i = 0; i < KEYS; i++) {
            counts.merge(ring.shardFor("k" + random.nextLong()), 1, Integer::sum);
        }
        assertThat(counts).containsOnlyKeys("shard1", "shard2");
        // each shard within 40–60% of the keys
        counts.forEach((shard, count) ->
                assertThat(count)
                        .as("share of shard %s", shard)
                        .isBetween((int) (KEYS * 0.40), (int) (KEYS * 0.60)));
    }

    @Test
    void addingThirdShardRemapsAboutOneThirdOfKeysAndOnlyToTheNewShard() {
        ConsistentHashRing two = new ConsistentHashRing(List.of("shard1", "shard2"), VNODES);
        ConsistentHashRing three = new ConsistentHashRing(List.of("shard1", "shard2", "shard3"), VNODES);
        Random random = new Random(13);
        int remapped = 0;
        for (int i = 0; i < KEYS; i++) {
            String key = "k" + random.nextLong();
            String before = two.shardFor(key);
            String after = three.shardFor(key);
            if (!before.equals(after)) {
                remapped++;
                // consistent hashing: a moved key can only move to the NEW shard
                assertThat(after).isEqualTo("shard3");
            }
        }
        double fraction = remapped / (double) KEYS;
        assertThat(fraction)
                .as("remap fraction going 2 -> 3 shards (expected ~1/3)")
                .isBetween(0.20, 0.45);
    }

    @Test
    void acceptsArbitraryStringKeys() {
        // Track A routes idempotency keys (UUID-ish strings), not just short codes
        ConsistentHashRing ring = new ConsistentHashRing(List.of("shard1", "shard2"), VNODES);
        for (int i = 0; i < 100; i++) {
            String idempotencyKey = UUID.randomUUID().toString();
            assertThat(ring.shardFor(idempotencyKey)).isIn("shard1", "shard2");
            assertThat(ring.shardFor(idempotencyKey)).isEqualTo(ring.shardFor(idempotencyKey));
        }
    }

    @Test
    void exposesTopology() {
        ConsistentHashRing ring = new ConsistentHashRing(List.of("shard1", "shard2"), VNODES);
        assertThat(ring.shards()).containsExactly("shard1", "shard2");
        assertThat(ring.vnodesPerShard()).isEqualTo(VNODES);
    }

    @Test
    void rejectsEmptyTopologyAndNonPositiveVnodes() {
        assertThatThrownBy(() -> new ConsistentHashRing(List.of(), VNODES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsistentHashRing(null, VNODES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsistentHashRing(List.of("shard1"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
