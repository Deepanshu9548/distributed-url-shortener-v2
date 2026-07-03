# ADR-007: Exactly-once EFFECT from at-least-once delivery

**Status:** Accepted (M0)

Kafka gives at-least-once (manual ack after processing). Aggregates must not
double-count on redelivery. Mechanism: every event carries a producer-side
`eventId` (UUID). Consumer, in ONE control-DB transaction:
1. INSERT raw event with eventId as PK — `ON CONFLICT DO NOTHING`.
2. Increment hourly aggregate ONLY if step 1 actually inserted a row.

Redelivery hits the PK conflict → aggregate untouched. This is the standard
"idempotent consumer" pattern; the raw table doubles as an audit/replay source.
Poison messages: 3 processing failures → DLQ topic, ack original, count.
