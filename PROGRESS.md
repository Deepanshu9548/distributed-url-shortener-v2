# PROGRESS — Checkpoint File

> **Resume protocol:** read this file top-to-bottom, then `git log --oneline`.
> Each milestone = one commit tagged `checkpoint/<milestone>`. The "NEXT" section
> says exactly what to do next. Update this file in every checkpoint commit.

## Environment notes
- Docker is NOT installed on the dev machine → compose stack and `@Tag("docker")`
  tests are validated in CI (ubuntu runners) or when Docker becomes available.
  Default `mvn verify` runs unit + contract tests only.
- Java 25 toolchain locally, project targets release 17 (works fine).

## Milestone status

| Milestone | Status | Checkpoint tag |
|---|---|---|
| M0 foundation (skeleton, contracts, stubs, compose, CI, docs) | ✅ DONE | `checkpoint/m0` |
| M1-A core (Snowflake, Base62, shorten/redirect, cache-aside) | ✅ DONE | `checkpoint/m1-a` |
| M1-B data (hash ring, shard routing, replicas) | ⬜ next | — |
| M1-C auth (JWT, users, ownership CRUD) | ⬜ | — |
| M1-D analytics (Kafka, consumers, stats) | ⬜ | — |
| M1-E rate limiting (Lua token bucket, filter) | ⬜ | — |
| M2 integration (swap stubs, filter order, e2e) | ⬜ | — |
| M3-F resilience (breakers, chaos, degradation matrix) | ⬜ | — |
| M3-G observability (metrics, dashboards, logs) | ⬜ | — |
| M4 load + packaging (JMeter, README, demo, defense notes) | ⬜ | — |

## NEXT
Execute M1-B (data track, territory `sharding/`): consistent-hash ring
(murmur3_32_fixed, 150 vnodes, key = short code), routing datasource, per-shard
programmatic Flyway consuming `db/migration/shard/V1__create_links.sql`
(already written by Track A), replica-preferring reads with single primary
fallback. Must pass `ShardRouterContractTest`; the real router is `@Primary`
over the SingleShardRouter stub. Remaining track order: B → E → C → D.
Each track: implement in its owned territory (see docs/OWNERSHIP.md), pass its
contract tests, `mvn verify` green, commit `checkpoint/m1-<track>`.

### Notes from M1-A for later tracks
- Shard schema frozen in `src/main/resources/db/migration/shard/V1__create_links.sql`:
  `links` (id BIGINT PK snowflake, short_code VARCHAR(32) + unique index
  ux_links_short_code, long_url VARCHAR(8192), user_id BIGINT nullable,
  created_at/expires_at TIMESTAMPTZ, is_custom_alias BOOLEAN) and
  `idempotency_keys` (key VARCHAR(128) PK, short_code, created_at). Cleanup of
  idempotency rows older than 24h is a documented TODO, not built.
- Idempotency rows are routed by the idempotency-key STRING (not a short code)
  — ShardRouter implementations must accept arbitrary string keys.
- `RedisUrlCache` (cache/) is `@Primary` over the NoopCache stub. Keys
  `url:{code}` and `lock:url:{code}` per ADR-004. Metrics
  cache.hits/misses/negative.hits/errors, all tagged source=redis. Unlock has
  no fencing token (documented javadoc limitation).
- `common/GlobalExceptionHandler` maps ValidationException→400,
  NotFoundException→404, AliasConflictException→409, InfraUnavailableException
  and DataAccessException→503; body always `{"error": "..."}`. Reuse it.
- Local JDK 25 quirks: Lombok annotation processing fails (write plain
  constructors, don't use @RequiredArgsConstructor); Mockito needs the subclass
  mock maker (`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`)
  → final classes cannot be mocked in tests.
- Controller slices use `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters=false)`
  until Track C lands real security config.

## Decisions already frozen (do not re-derive — see docs/adr/)
Snowflake 41/10/12 epoch 2024-01-01 · Base62 · 302 not 301 · murmur3_32_fixed +
150 vnodes, key = short code · cache-aside, negative cache "__NOT_FOUND__" 60s,
TTL min(24h, expiry) · stampede lock SET NX PX 2000 · token bucket via Lua,
hash-tag keys, write/auth fail-closed + redirect fail-open · acks=all,
max.block.ms=0, partition key = short code · eventId raw-insert idempotency
gate · link-events topic feeds user_links (no dual-write) · control DB off hot
path · 503 (infra) vs 404 (unknown) on redirect · JWT 15m/7d refresh rotation,
Redis denylist fail-open · alias blocklist {api,auth,actuator,swagger-ui,metrics,health,admin}
· max URL 8192 · no long-URL dedup · Idempotency-Key on create, 24h window.
