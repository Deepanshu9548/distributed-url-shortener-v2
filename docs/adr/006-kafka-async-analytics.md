# ADR-006: Kafka for async analytics; bounded-loss durability SLO

**Status:** Accepted (M0)

## Decision
Clicks publish to `click-events` (6 partitions, key = short code → per-link
ordering, cross-link parallelism). Producer: `acks=all`,
`enable.idempotence=true`, `max.block.ms=0` — the redirect thread NEVER blocks
on messaging; if the producer buffer is full, the send fails instantly and a
counter increments.

## Why Kafka over RabbitMQ
Replayable log (reprocess aggregates after a consumer bug), consumer groups
give per-partition ordering + horizontal consumers, and throughput headroom.
RabbitMQ's strengths (routing topology, per-message TTL) aren't needed here.

## Durability — stated honestly
"Clicks must not be lost" is implemented as a bounded-loss SLO:
- Normal operation & Kafka outages shorter than buffer capacity: zero loss
  (acks=all + retries + idempotent producer).
- Beyond that (buffer full, or app crash with in-flight events): loss is
  COUNTED (`analytics.events.failed/dropped`), alertable, and reconciled in
  load tests (JMeter count == aggregated + counted-failed).
True zero-loss needs a producer-side WAL/outbox on the redirect path — rejected
because it puts a disk write on the hot path. This tradeoff is a feature of the
design, not a bug; see defense notes.
