# Distributed URL Shortener v2 - Context & Relationships

This file serves as the definitive context for future agents and developers continuing work on this repository.

## 1. Project Overview & Architecture
A high-performance, distributed URL shortener designed for horizontal scalability, fault tolerance, and high availability.
- **Language/Framework**: Java 17+ (Compiled for 17, JDK 25 toolchain), Spring Boot 3.3.x
- **Datastore**: 
  - **Shard DBs (PostgreSQL)**: Stores actual short-links and idempotency keys. Horizontally scaled using consistent hashing. Primary/Replica streaming replication.
  - **Control DB (PostgreSQL/H2)**: Off the hot path. Stores users, JWT ownership, and analytics stats.
- **Cache & Rate Limiting**: Redis. Cache-aside pattern for links. Token bucket (Lua script) for rate limiting.
- **Asynchronous Analytics**: Kafka. Fire-and-forget publishing. Consumers ensure exactly-once processing via raw event deduplication.
- **Authentication**: JWT (Access + Refresh tokens).

## 2. Codebase Structure & Ownership
The codebase is structured as package-by-feature under `io.portfolio.urlshortener`:
- `contracts/`: FROZEN interfaces (e.g., `ShardRouter`, `UrlCache`, `EventPublisher`). Never modify.
- `shortener/`: Core link generation (Snowflake ID, Base62), routing, and controllers. (M1-A/M1-B)
- `sharding/`: Multi-datasource routing (`RoutingDataSource`), Hash Ring implementation. (M1-B)
- `cache/`: Redis cache implementation for links.
- `ratelimit/`: Token bucket implementation and Servlet Filter. (M1-E)
- `auth/`: Users, JWT Service, Security configuration, Control DB config. (M1-C)
- `analytics/` & `events/`: Kafka producers/consumers, link stats, exactly-once processing. (M1-D)

## 3. Key Design Decisions (ADRs)
- **ADR-004**: Cache-aside with negative caching (60s TTL for `__NOT_FOUND__`) to prevent cache penetration.
- **ADR-007**: Exactly-once analytics processing using `eventId` raw-inserts in the control DB.
- **ADR-010**: Control DB separate from Shard Ring to isolate analytical/auth load from the hot path.
- **Idempotency**: 24-hour TTL on idempotency keys to prevent duplicate link creation on retries.

## 4. Current State & Milestones
- ✅ **M0**: Foundation, contracts, stubs, docker-compose.
- ✅ **M1-A**: Core shortening, caching, exception handling.
- ✅ **M1-B**: Hash ring, shard routing, replicas.
- ✅ **M1-E**: Rate limiting (Redis token bucket).
- ✅ **M1-C**: Auth (JWT, ownership).
- ✅ **M1-D**: Analytics (Kafka).
- ✅ **M2**: Integration (Wiring, filter ordering, end-to-end tests).
- ⬜ **M3-F**: Resilience (Resilience4j breakers, chaos testing, degradation). -> **NEXT**
- ⬜ **M3-G**: Observability (Metrics, dashboards).
- ⬜ **M4**: Load testing & Packaging.

## 5. Agent Instructions
- **Rule of Thumb**: `mvn -q verify` MUST pass. One milestone = one commit (`m<N>(<track>): <what>`) + tag (`checkpoint/<milestone>`).
- **Do NOT** rewrite git history.
- Always read `PROGRESS.md` for the current exact state and `docs/adr/` for frozen architectural decisions.
- Use `@Primary` and `@ConditionalOnProperty` to supersede M0 stubs with real implementations.
