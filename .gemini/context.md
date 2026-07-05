# Context Tracker

This file tracks the overarching context and completed milestones for the Distributed URL Shortener project to aid future agents and contributors.

## Project Goal
A portfolio-grade distributed URL shortener demonstrating sharding, consistent hashing, cache-aside reads, asynchronous analytics, distributed rate limiting, and robust resilience engineering.

## Current State (as of completion of Phase 3 / M4)
- **Branch:** `agent-updates-m3m4` based off `main`.
- **Framework:** Spring Boot 3.3.5 / Java 25.

### Completed Milestones
- **M0, M1-A through M1-E, M2:** Foundation, API, Cache, Auth, Kafka Analytics, Sharding, Rate Limiting (Completed by previous agents, tagged on main).
- **M3-F Resilience (Completed):** Resilience4j circuit breakers applied to Redis (Cache/RateLimit), Kafka, and Shards. Fallback semantics preserved. Chaos tests added. Degradation matrix documented.
- **M3-G Observability (Completed):** RequestIdFilter added for MDC context. Logback structured JSON logging enabled. Grafana dashboards created.
- **M4 Load + Packaging (Completed):** JMeter test plans for read/write load testing. E2E demo scripts. README and architectural defense notes completed.

## Architecture Highlights
- **Sharding:** Consistent Hash Ring (150 vnodes, murmur3_32_fixed).
- **Resilience:** Circuit Breakers isolate shard failures.
- **Cache:** Cache-aside with Stampede Lock and negative caching.
- **Auth:** Stateless JWT with Redis fail-open denylist.
- **Events:** Asynchronous Kafka publishing for analytics.

## Future Context / Next Steps
- The next major chunk of work is a migration to **Spring Boot 4 / Framework 7**, which will likely include Project Loom refactoring. (See `suggestions.md` for broader ideas).
