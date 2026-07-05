package io.portfolio.urlshortener.shortener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    private static final int THREADS = 4;
    private static final int IDS_PER_THREAD = 50_000;

    @Test
    void allIdsDistinctUnderConcurrency() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                List<Long> ids = new ArrayList<>(IDS_PER_THREAD);
                for (int i = 0; i < IDS_PER_THREAD; i++) {
                    ids.add(generator.nextId());
                }
                return ids;
            }));
        }
        start.countDown();
        Set<Long> all = new HashSet<>(THREADS * IDS_PER_THREAD);
        for (Future<List<Long>> f : futures) {
            all.addAll(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(all).hasSize(THREADS * IDS_PER_THREAD);
    }

    @Test
    void idsAreMonotonicPerThread() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            futures.add(pool.submit(() -> {
                long previous = -1;
                for (int i = 0; i < IDS_PER_THREAD; i++) {
                    long id = generator.nextId();
                    if (id <= previous) {
                        return false;
                    }
                    previous = id;
                }
                return true;
            }));
        }
        for (Future<Boolean> f : futures) {
            assertThat(f.get(60, TimeUnit.SECONDS)).as("strictly increasing per thread").isTrue();
        }
        pool.shutdown();
    }

    @Test
    void sequenceExhaustionWaitsForNextMillisecond() {
        // Frozen clock: 4096 ids fit in one ms; the 4097th must advance the clock.
        AtomicLong clock = new AtomicLong(SnowflakeIdGenerator.EPOCH_MILLIS + 1_000);
        AtomicLong reads = new AtomicLong();
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, () -> {
            // after enough spin reads, let time advance so the wait terminates
            if (reads.incrementAndGet() > 5_000) {
                clock.incrementAndGet();
            }
            return clock.get();
        });
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            ids.add(generator.nextId());
        }
        assertThat(ids).hasSize(5_000);
    }

    @Test
    void smallBackwardDriftIsWaitedOut() {
        long base = SnowflakeIdGenerator.EPOCH_MILLIS + 5_000;
        // t, then t-3 (within tolerance), then recovered values
        AtomicLong calls = new AtomicLong();
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, () -> {
            long n = calls.incrementAndGet();
            if (n == 1) {
                return base;
            }
            if (n == 2) {
                return base - 3;
            }
            return base + n; // clock recovered and moves forward
        });
        long first = generator.nextId();
        long second = generator.nextId();
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void largeBackwardDriftThrows() {
        long base = SnowflakeIdGenerator.EPOCH_MILLIS + 5_000;
        AtomicLong calls = new AtomicLong();
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, () ->
                calls.incrementAndGet() == 1 ? base : base - 100);
        generator.nextId();
        assertThatThrownBy(generator::nextId)
                .isInstanceOf(ClockMovedBackwardsException.class)
                .isInstanceOf(InfraUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1024, Long.MIN_VALUE, Long.MAX_VALUE})
    void nodeIdOutsideRangeIsRejected(long nodeId) {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(nodeId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("node-id");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 512, 1023})
    void nodeIdWithinRangeIsAccepted(long nodeId) {
        assertThatCode(() -> new SnowflakeIdGenerator(nodeId)).doesNotThrowAnyException();
    }

    @Test
    void bitLayoutEmbedsNodeIdAndTimestamp() {
        long fixedNow = SnowflakeIdGenerator.EPOCH_MILLIS + 123_456;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(42, () -> fixedNow);
        long id = generator.nextId();
        assertThat((id >> 22)).isEqualTo(123_456);        // 41-bit timestamp delta
        assertThat((id >> 12) & 0x3FF).isEqualTo(42);     // 10-bit node id
        assertThat(id & 0xFFF).isEqualTo(0);              // first sequence in the ms
    }
}
