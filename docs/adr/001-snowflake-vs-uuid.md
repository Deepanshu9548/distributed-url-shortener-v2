# ADR-001: Snowflake IDs over UUID/DB-sequence

**Status:** Accepted (M0)

## Decision
Custom Snowflake: 1 sign bit + 41-bit ms timestamp (epoch 2024-01-01T00:00:00Z)
+ 10-bit node id (env `NODE_ID`) + 12-bit sequence. Base62-encoded → short code.

## Why not UUID
128 bits → 22+ Base62 chars (unusably long short-link), random → index-hostile
(B-tree page splits), no time ordering.

## Why not DB auto-increment
Central sequence = SPOF + cross-shard coordination for uniqueness; Snowflake is
coordination-free per node (uniqueness = node-id disjointness + per-node
sequence) and generates in-process (~ns, no network hop on the write path).

## Consequences
- Clock discipline required: generator refuses to mint on backward drift
  (throws), sequence exhaustion spin-waits to next ms.
- Node-id assignment via env var per container — acceptable for a fixed local
  topology; a real fleet would lease ids (ZooKeeper/etcd or K8s StatefulSet
  ordinal). Documented tradeoff, not built.
- IDs leak creation-time — acceptable for this domain.
