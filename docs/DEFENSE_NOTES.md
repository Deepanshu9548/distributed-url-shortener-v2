# Architectural Defense Notes

This document provides interview-ready reasoning for the architectural decisions made in the Distributed URL Shortener.

## 1. Snowflake IDs vs UUIDs
We chose a distributed 64-bit Snowflake ID (41 bits timestamp, 10 bits machine/shard ID, 12 bits sequence) encoded in Base62 instead of UUIDs.
**Why:**
- **Size & Aesthetics:** UUIDs are 128-bit and result in long 22-character Base62 strings. Snowflake IDs yield 11-character strings (or shorter depending on the epoch), which are much more user-friendly for a short URL service.
- **Index Locality:** Snowflake IDs are roughly time-ordered. This guarantees chronological locality when inserting into the database, dramatically reducing page fragmentation and write overhead in B-Trees compared to the random distribution of UUIDs.
- **Epoch Choice:** We use a custom epoch to maximize the lifespan of the 41-bit timestamp component without overlapping into negative space.

## 2. Consistent Hashing
Routing is managed by a consistent hash ring using `murmur3_32_fixed` with 150 virtual nodes per physical shard.
**Why:**
- **Murmur3:** It provides excellent avalanche properties and collision resistance for non-cryptographic distribution, executing significantly faster than SHA-256 while ensuring an even distribution of keys.
- **150 Virtual Nodes:** Using 150 vnodes smooths out the distribution variance. If we add or remove a shard, only `1/N` of the keys map to a new node, minimizing cache stampedes and database churn compared to simple modulo hashing (`hash(key) % N`).

## 3. Cache-Aside + Negative Cache + Stampede Lock
We use a cache-aside pattern with Redis, augmented by negative caching and a lock for missing keys.
**Why:**
- **Not Read-Through:** Read-through requires the cache layer to understand how to fetch from the primary store. Cache-aside allows the application layer to orchestrate the fetch and fallback gracefully if the cache is unavailable, preserving separation of concerns.
- **Negative Caching:** We cache `404 Not Found` results (empty strings) to prevent attackers from intentionally exhausting database resources by repeatedly requesting non-existent keys.
- **Stampede Lock:** Using a `SETNX` lock when a key expires prevents the "thundering herd" problem, where thousands of concurrent requests all hit the database simultaneously to recompute the same missing cache value.

## 4. Token Bucket in Lua
Rate limiting is enforced via a Token Bucket algorithm evaluated in a single atomic Redis Lua script.
**Why:**
- **Atomicity:** Lua scripts in Redis run atomically. This prevents race conditions where two concurrent requests read the same bucket capacity and both are allowed through when only one token remains.
- **Fail Policy:** The rate limiter defaults to "fail-open" for critical reads (redirects) so that a Redis outage doesn't bring down the core redirect service. Writes (shortening) are configured to "fail-closed" to prevent unbounded spam during an outage.

## 5. Kafka Async Click Pipeline
Click analytics are published to a Kafka topic and processed asynchronously.
**Why:**
- **Latency & Throughput:** Synchronously updating the click count in the database would add tremendous latency and contention to the `GET /{code}` path, severely limiting redirect throughput.
- **Exactly-once-effect:** Kafka guarantees at-least-once delivery. The consumer uses a batching approach and upserts stats idempotently in the database. We prioritize fast, highly-available redirects over 100% synchronous analytical consistency.

## 6. Stateless JWT & Redis Denylist
Authentication relies on stateless JWTs with a 15-minute access token, a 7-day refresh token, and a Redis denylist for early revocation.
**Why:**
- **Statelessness:** Eliminates the need for a central session store to validate every request, allowing the API tier to scale horizontally without state bottlenecks.
- **Denylist Fail-Open:** If the Redis instance hosting the denylist goes down, the API defaults to trusting mathematically valid JWTs (fail-open) to maintain availability, relying on the short 15-minute expiration to limit exposure.

## 7. Control DB Off the Ring
The central database handling user accounts, JWT issuance, and routing metadata is completely isolated from the sharded link storage.
**Why:**
- **Separation of Concerns:** Shards should only be responsible for massive, partitioned data sets (the links). The control plane requires strong consistency but has far lower volume, so it lives in a dedicated, unsharded RDBMS.

## 8. Circuit Breakers per Dependency
We use Resilience4j to wrap all external dependency calls (Redis cache, Redis rate limiter, Kafka publisher, and each individual Database shard).
**Why:**
- **Per-Shard Isolation:** A circuit breaker is instantiated per shard. If Shard 1 experiences a hardware failure, its circuit breaker opens, failing fast to prevent thread pool exhaustion, while Shard 2 continues serving traffic completely unaffected.
- **Fallback Semantics:** The fallbacks strictly preserve the degradation contracts (e.g., if the cache is down, fall back to DB; if Kafka is down, drop the analytics event but execute the redirect).

## What I'd Do Next (Suggestions)
- Investigate **Project Loom (Virtual Threads)** in Spring Boot 3.2+ for I/O bound operations to reduce memory footprint per concurrent connection.
- Implement **Edge Caching** (e.g., Cloudflare Workers or Fastly) for hot links, eliminating the need for requests to even hit our infrastructure.
- Upgrade to **Spring Boot 4 / Framework 7** once released, leveraging GraalVM native image compilation for instant startup times in a Kubernetes/Serverless environment.
