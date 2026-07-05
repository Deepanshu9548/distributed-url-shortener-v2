package io.portfolio.urlshortener.shortener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * Snowflake ID generator per ADR-001: 1 sign bit + 41-bit millisecond timestamp
 * (epoch 2024-01-01T00:00:00Z) + 10-bit node id + 12-bit per-node sequence.
 *
 * <p>Coordination-free uniqueness: node ids are disjoint (env {@code NODE_ID},
 * validated 0–1023) and the sequence is per-node. Thread-safe via a single
 * synchronized minting method. On sequence exhaustion within one millisecond
 * the generator spin-waits to the next millisecond. Backward clock drift up to
 * {@link #BACKWARD_TOLERANCE_MS} is waited out; larger drift throws
 * {@link ClockMovedBackwardsException} (mapped to 503 — refusing to mint beats
 * risking duplicate ids).
 */
@Component
public class SnowflakeIdGenerator {

    /** 2024-01-01T00:00:00Z — frozen in ADR-001 and application.yml. */
    public static final long EPOCH_MILLIS = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();

    static final long MAX_NODE_ID = 1023;   // 10 bits
    static final long MAX_SEQUENCE = 4095;  // 12 bits
    static final long BACKWARD_TOLERANCE_MS = 5;

    private static final int NODE_SHIFT = 12;
    private static final int TIMESTAMP_SHIFT = 22;

    private final long nodeId;
    private final LongSupplier clock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    @org.springframework.beans.factory.annotation.Autowired
    public SnowflakeIdGenerator(@Value("${app.node-id}") long nodeId) {
        this(nodeId, System::currentTimeMillis);
    }

    SnowflakeIdGenerator(long nodeId, LongSupplier clock) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(
                    "app.node-id must be in [0, " + MAX_NODE_ID + "] but was " + nodeId);
        }
        this.nodeId = nodeId;
        this.clock = clock;
    }

    /** Mints the next id. Monotonic per node, unique across threads. */
    public synchronized long nextId() {
        long now = clock.getAsLong();

        if (now < lastTimestamp) {
            long drift = lastTimestamp - now;
            if (drift > BACKWARD_TOLERANCE_MS) {
                throw new ClockMovedBackwardsException(drift);
            }
            now = waitUntil(lastTimestamp);
        }

        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 4096 ids minted this millisecond — wait for the next one.
                now = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = now;
        return ((now - EPOCH_MILLIS) << TIMESTAMP_SHIFT) | (nodeId << NODE_SHIFT) | sequence;
    }

    private long waitUntil(long targetMillis) {
        long now = clock.getAsLong();
        while (now < targetMillis) {
            Thread.onSpinWait();
            now = clock.getAsLong();
        }
        return now;
    }
}
