# ADR-004: Cache-aside with negative caching and stampede lock

**Status:** Accepted (M0)

## Decision
Redis cache-aside on `url:{code}` → long URL. Cache TTL = min(24h, link
expiry). Negative sentinel `__NOT_FOUND__` @ 60s. Stampede lock:
`SET lock:url:{code} NX PX 2000`; non-holders retry the cache 3×50ms then read
DB *without* populating. All cache errors degrade to Miss/no-op — Redis down
never fails a redirect.

## Why cache-aside over write-through
Create-then-never-clicked links are common (bots, tests); write-through wastes
cache on them. Cache-aside keeps the cache exactly as hot as real traffic.
Failure mode is simpler: cache is strictly an optimization layer.

## Why negative caching
Random-code scans would otherwise punch through to the DB on every miss
(cache penetration). 60s sentinel bounds that to 1 DB read/code/minute.
