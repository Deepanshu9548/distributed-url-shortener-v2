# ADR-002: Consistent hashing (vnodes) over range sharding, key = short code

**Status:** Accepted (M0)

## Decision
murmur3_32_fixed over the short code; ring with 150 virtual nodes per physical
shard in a TreeMap; writes → owning shard primary, reads → its replica with
single primary fallback.

## Why key = short code (not user id)
The redirect is the hot path and carries only the code — hashing the code
resolves the shard with zero lookups. User-keyed sharding would need a
code→user index lookup before every redirect.

## Why consistent hashing over range sharding
Range sharding on Snowflake-prefixed codes = time-ordered keys = the newest
(hottest) links all land on one shard. Consistent hashing spreads uniformly;
vnodes bound remapping to ~1/N keys when adding shard N+1.

## Why not range + rebalancer (Vitess-style)
Operationally heavier than the project needs; the ring demonstrates the same
interview-relevant reasoning with measurable distribution stats.

## Consequences
- "List my links" can't be served by shards directly → secondary index
  (user_links on control DB, fed by link-events; ADR-008).
- Adding a shard remaps ~1/N keys whose cached entries simply repopulate;
  DB-side data movement is out of scope and documented as such.
