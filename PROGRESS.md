# PROGRESS — Checkpoint File

> **Resume protocol:** read this file top-to-bottom, then `git log --oneline`.
> Each milestone = one commit tagged `checkpoint/<milestone>`. The "NEXT" section
> says exactly what to do next. Update this file in every checkpoint commit.

## Environment notes
- Docker is NOT installed on the dev machine → compose stack and `@Tag("docker")`
  tests are validated in CI (ubuntu runners) or when Docker becomes available.
  Default `mvn verify` runs unit + contract tests only.
- Java 25 toolchain locally, project targets release 17 (works fine).

## ⚠️ SECURITY TODO (before any `git push` / publishing this repo)
`.claude/settings.json` holds a live local API key and is TRACKED by git
(since m0). It has been added to .gitignore, but the untrack command
(`git rm --cached .claude/settings.json`) has been repeatedly blocked by
the auto-mode Bash classifier — the user must run it manually:
```
git rm --cached .claude/settings.json
git commit -m "chore: untrack local credentials file"
```
Then, before any push: either rotate the key (accepting the dead key in
history) or rewrite history to purge the file from m0/m1-a/m1-b/m1-e
commits (re-tagging the checkpoints afterwards).

## Milestone status

| Milestone | Status | Checkpoint tag |
|---|---|---|
| M0 foundation (skeleton, contracts, stubs, compose, CI, docs) | ✅ DONE | `checkpoint/m0` |
| M1-A core (Snowflake, Base62, shorten/redirect, cache-aside) | ✅ DONE | `checkpoint/m1-a` |
| M1-B data (hash ring, shard routing, replicas) | ✅ DONE | `checkpoint/m1-b` |
| M1-E rate limiting (Lua token bucket, filter) | ✅ DONE | `checkpoint/m1-e` |
| M1-C auth (JWT, users, ownership CRUD) | ⬜ next | — |
| M1-D analytics (Kafka, consumers, stats) | ⬜ | — |
| M2 integration (swap stubs, filter order, e2e) | ⬜ | — |
| M3-F resilience (breakers, chaos, degradation matrix) | ⬜ | — |
| M3-G observability (metrics, dashboards, logs) | ⬜ | — |
| M4 load + packaging (JMeter, README, demo, defense notes) | ⬜ | — |

## NEXT
Execute M1-C (auth track, territory `auth/`). Frozen scope:
- **Control DB (ADR-010)**: OWN qualified DataSource + EntityManagerFactory +
  TransactionManager (do NOT share the shard `routingDataSource`, which is
  `@Primary` when sharding is on). Flyway location
  `classpath:db/migration/control` (new). Config keys under `app.control-db`
  (env-driven; independent of `SHARD*_URL`).
- **Schema (control DB)**: `users` (id BIGINT PK snowflake OR bigserial —
  pick snowflake to reuse the generator + stay off DB sequences; email
  citext unique, password_hash bcrypt, created_at); `user_links` (user_id,
  short_code PK+FK-ish, created_at) — this is the LinkIndex fed by
  Track D's link-events consumer; C DEFINES the table + a read-only
  `LinkIndexRepository` and hands it to Track D.
- **JWT**: access 15m HS256 (secret from `JWT_SECRET`, already in
  `.env.example`), refresh 7d rotation (persist refresh-family/session id
  in Redis or control DB — choose Redis with fail-OPEN denylist per
  frozen decisions; access token bears user id + roles).
- **Endpoints under `/api/auth/**`** (rate-limited by M1-E as `auth`):
  `POST /register` (email+password → 201 minimal profile),
  `POST /login` (→ access+refresh),
  `POST /refresh` (rotate),
  `POST /logout` (denylist the refresh family + current access token JTI).
- **Spring Security config**: public paths = `GET /{code}` (regex-guarded
  as in the redirect controller), `POST /api/auth/**`, `/actuator/health`,
  `/actuator/prometheus`, `/actuator/info`, `/swagger-ui/**`, `/v3/api-docs/**`;
  everything else under `/api/**` → JWT-authenticated. Filter order (with
  M1-E in mind, per its "Notes"): `InstanceHeaderFilter` (HIGHEST) →
  request-id (M3-G will add) → `RateLimitFilter` → JWT auth →
  authorization. `RateLimitFilter` must run BEFORE JWT auth so that
  `POST /api/auth/login` gets IP-bucketed (no principal yet) and other
  authenticated writes get user-bucketed (M1-E already reads
  SecurityContextHolder → wire the filters in that order).
- **Ownership CRUD** on `/api/links` for the caller's own links:
  `GET /api/links` (paged list of the caller's short codes via `user_links`),
  `GET /api/links/{code}` (already public metadata read from M1-A — decide:
  keep public per README, or gate; frozen answer = keep public, but a new
  `GET /api/me/links` gives the paged user list),
  `PUT /api/links/{code}` (owner-only: update long_url/expires_at),
  `DELETE /api/links/{code}` (owner-only: soft-delete? frozen = hard delete
  + LinkEvent.DELETED). Ownership check reads `user_links` in the control DB
  (fast, off the shard hot path).
- **Supersede pattern**: no stub to supersede for auth (Spring Security is
  the "stub" via default in-memory user + password log line — replace with
  our JwtAuthenticationFilter + UserDetailsService).
- **Testing**: unit tests for JwtService (sign/verify, exp, refresh
  rotation), UserService (bcrypt, register/login idempotency), controller
  slices for /api/auth/** endpoints, security config (public vs protected
  routes), ownership guard. All Docker-free — no Postgres needed in unit
  tests (repositories mocked). Control-DB migration ITs behind `@Tag("docker")`.

Each track: implement in its owned territory (see docs/OWNERSHIP.md), pass
its contract tests, `mvn verify` green, commit `checkpoint/m1-<track>`.
After C: **M1-D** (analytics: Kafka publisher supersedes NoopPublisher,
click + link-events consumers, `link-events` feeds `user_links`; ADR-006/
007/008 frozen).

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

### Notes from M1-E for later tracks
- Package `ratelimit/`. Beans (both @ConditionalOnProperty on
  `ratelimit.enabled=true`, matchIfMissing=true so unit slices work):
  `RedisTokenBucketRateLimiter` (@Primary — wins over the AllowAllRateLimiter
  stub) and `RateLimitFilter`. Turning the flag off drops both → the stub
  becomes the sole `RateLimiter` and no filter runs.
- Atomic Lua script at `src/main/resources/lua/token_bucket.lua`. KEYS[1]=key,
  ARGV = capacity, refill_per_ms (double), now_ms, requested. Returns
  {allowed, retry_after_ms}. Deterministic (clock via ARGV) → Redis Cluster /
  replication safe. PEXPIRE = 2×(capacity/refill_per_ms) so idle buckets
  self-clean. Guards against clock-backwards by clamping elapsed to 0.
- Bucket key format (Track C's JWT filter will drive this):
  `rl:{<subject>}:<limiter>` — hash-tag braces around SUBJECT.
  Subject: `user:<username>` when SecurityContextHolder has a non-anonymous
  authentication; else `ip:<addr>` (first X-Forwarded-For entry, else
  remoteAddr). XFF is trusted blindly — javadoc'd; nginx sets it in compose.
- **Filter ordering (M2 must wire this)**: `InstanceHeaderFilter`
  (HIGHEST_PRECEDENCE, already set) → request-id (M3-G) → `RateLimitFilter`
  → JWT auth (M1-C) → Spring Security authz. Ordering matters: login
  requests hit rate limit BEFORE authentication (IP-bucketed);
  authenticated writes hit rate limit AFTER (user-bucketed via
  SecurityContextHolder). M1-E leaves `@Order` unset so M2 owns this.
- Metric: `ratelimit.decisions` tags `limiter=<name>,
  outcome=allowed|denied|fail_open|fail_closed` (allowed labels only —
  NEVER key/subject).
- Failure policy is PER LIMITER via `ratelimit.limiters.<name>.fail-open`.
  application.yml already has: write/auth failOpen=false, redirect
  failOpen=true. Filter translates fail-closed store-unavailable → 503
  {"error":"rate limiter unavailable"}; denied → 429 with Retry-After
  header (ceil seconds, min 1) + {"error":"rate limit exceeded"}.
- Routing table (first-match): `/api/auth/**` → auth; `/api/links**` +
  write method → write; single-segment `/{code}` regex GET
  (excludes /api/, /actuator/, /swagger-ui/, /v3/api-docs, /favicon.ico)
  → redirect; anything else → chain.doFilter unchanged (no limit).
- NO docker IT for the Lua script yet (unit-mocked template + inline
  algorithm review). Add when Docker becomes available or in the M2
  integration pass.

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
