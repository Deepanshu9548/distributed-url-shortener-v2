# ADR-005: Token bucket via Redis Lua; asymmetric failure policy

**Status:** Accepted (M0)

## Decision
Token bucket state (`tokens`, `ts`) in a Redis hash; refill + check + decrement
in ONE Lua script (atomic — no race between app instances). Keys use hash tags
`rl:{user:42}:write` so all operations on one bucket hit one cluster slot →
the design survives a move to Redis Cluster unchanged. PEXPIRE = full-refill
time keeps idle buckets self-cleaning.

## Why token bucket over sliding window log
O(1) memory per key vs O(requests); burst tolerance up to capacity is the
desired UX for a write API. Sliding-window-log's precision isn't worth the
memory at redirect volumes.

## Failure policy (asymmetric, deliberate)
Redis down → redirects FAIL-OPEN (availability of the core product wins),
writes/auth FAIL-CLOSED 503 (abuse surface + they're already DB-bound).
This asymmetry is tested (chaos) and documented in the degradation matrix.
