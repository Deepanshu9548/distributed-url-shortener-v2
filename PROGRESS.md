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
| M1-B data (hash ring, shard routing, replicas) | ✅ DONE | `checkpoint/m1-b` |
| M1-C auth (JWT, users, ownership CRUD) | ⬜ | — |
| M1-D analytics (Kafka, consumers, stats) | ⬜ | — |
| M1-E rate limiting (Lua token bucket, filter) | ⬜ next | — |
| M2 integration (swap stubs, filter order, e2e) | ⬜ | — |
| M3-F resilience (breakers, chaos, degradation matrix) | ⬜ | — |
| M3-G observability (metrics, dashboards, logs) | ⬜ | — |
| M4 load + packaging (JMeter, README, demo, defense notes) | ⬜ | — |

## NEXT
Execute M1-E (rate-limit track, territory `ratelimit/` + `resources/lua/`):
Redis token bucket in an atomic Lua script per ADR-005 — hash-tag keys (e.g.
`rl:{<subject>}:<limiter>` so all keys of one bucket share a slot), limiters
write/redirect/auth with config already present in application.yml
(`ratelimit` section: capacity, refill-per-minute, fail-open). Semantics
frozen: write/auth fail-CLOSED, redirect fail-OPEN when Redis is unavailable
(surface via RateLimitResult.storeUnavailable, decide in the filter). Must
supersede the `AllowAllRateLimiter` stub the same way M1-B superseded
SingleShardRouter: real bean `@Primary` + `@ConditionalOnProperty` on the
feature flag (`ratelimit.enabled`), stub stays unconditional; verify Spring
resolves the primary cleanly and the stub still compiles. Metrics: lowercase
dot-separated, allowed labels only (limiter, outcome). Tests must run without
Docker (script logic testable via embedded/fake or unit-level seams; real
Redis behavior behind `@Tag("docker")`). Then remaining track order: C → D.
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

### Notes from M1-B for later tracks
- Sharding is OFF by default (`app.sharding.enabled=false` → the
  SingleShardRouter stub stays the ShardRouter bean; no shard datasources are
  built, so unit/slice tests need nothing). Enable with `SHARDING_ENABLED=true`
  — compose sets it on the app containers. Config: application.yml
  `app.sharding` (vnodes 150; shard1/shard2 each primary+replica). Env vars:
  SHARD1_PRIMARY_URL, SHARD1_REPLICA_URL, SHARD2_PRIMARY_URL,
  SHARD2_REPLICA_URL, SHARD_DB_USER, SHARD_DB_PASSWORD, SHARD_POOL_SIZE
  (defaults = compose hostnames; localhost ports 5433–5436 documented in
  .env.example for host-side runs).
- Beans when enabled (`sharding/` package): `routingDataSource` (@Primary
  DataSource, class RoutingDataSource, lookup keys `<shard>:PRIMARY|REPLICA`
  from the ShardContext ThreadLocal, default target = shard1 primary — JPA and
  Hibernate ddl-validate bind to it) built by `ShardDataSourceConfig`, which
  also runs programmatic Flyway (`classpath:db/migration/shard`) against every
  shard PRIMARY at startup (replicas get schema via streaming replication).
  Hikari: connection-timeout 1s; primary pools fail fast at boot, replica
  pools init lazily (a replica still in pg_basebackup never blocks boot).
- `HashRingShardRouter` (@Component @Primary
  @ConditionalOnProperty(app.sharding.enabled=true)): write→primary,
  read→replica with EXACTLY ONE primary fallback on connection-acquisition
  failures (DataAccessResourceFailureException — covers
  CannotGetJdbcConnectionException — and CannotCreateTransactionException,
  which transactional repo calls throw when they can't get a connection).
  Business exceptions propagate untouched, no fallback. Context always cleared
  in finally; `ShardContext.current()` is public for metrics/tests.
- Metric: `shard.route.total{shard=<name>, outcome=primary|replica|replica_fallback}`,
  counted once per completed routed operation.
- IMPORTANT for Track C: the shard `routingDataSource` is the @Primary
  DataSource. The control DB (ADR-010, off the ring) needs its OWN explicitly
  qualified DataSource + EntityManagerFactory/TransactionManager beans — do
  not lean on default JPA wiring for control-DB entities, and keep control
  repositories out of ShardRouter scopes.
- Stub-supersede pattern for Track E (AllowAllRateLimiter): real bean
  @Primary + @ConditionalOnProperty on the feature flag; stub stays
  unconditional in contracts/stubs. Both certified by the same abstract
  contract test (see HashRingShardRouterContractTest for the shape).
- Compose already had shard1/shard2 primary+replica with real streaming
  replication from M0 (init-primary.sh replication role + pg_basebackup
  replica entrypoints); M1-B only added app env (SHARDING_ENABLED=true) and
  replica depends_on. Docker still absent locally → compose stack + sharded
  end-to-end remain pending CI/manual validation (M2 integration).

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
