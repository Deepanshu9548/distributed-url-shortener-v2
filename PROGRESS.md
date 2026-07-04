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
| M1-C auth (JWT, users, ownership CRUD) | ✅ DONE | `checkpoint/m1-c` |
| M1-D analytics (Kafka, consumers, stats) | ✅ DONE | `checkpoint/m1-d` |
| M2 integration (swap stubs, filter order, e2e) | ✅ DONE | `checkpoint/m2` |
| M3-F resilience (breakers, chaos, degradation matrix) | ⬜ next | — |
| M3-G observability (metrics, dashboards, logs) | ⬜ | — |
| M4 load + packaging (JMeter, README, demo, defense notes) | ⬜ | — |

## NEXT
Execute M3-F resilience.
Goal: Resilience4j breakers on DB/Redis/Kafka calls, chaos tests, degradation matrix doc using the frozen decisions list.

### Notes from M2 for later tracks
- `ShardJpaConfig` has explicitly declared `@Primary` on its `EntityManagerFactory` and `TransactionManager` to avoid ambiguity with `ControlDbConfig`'s beans when both are loaded in context.
- EndToEnd test (`EndToEndIT`) verifies happy-path utilizing Testcontainers. Runs via `-Pdocker-tests`.
- The rate-limit filter is proven to execute *before* the JWT filter, ensuring that unauthenticated brute-force requests exhaust IP rate buckets before causing database load or hitting authentication logic.


### Notes from M1-D for later tracks
- Analytics tables (`raw_click_events`, `link_stats`, `raw_link_events`) created in `V2__create_analytics.sql` (Control DB).
- `KafkaEventPublisher` operates conditionally on `app.kafka.enabled` and publishes to `click-events` and `link-events`.
- `ClickConsumer` and `LinkEventConsumer` use raw insert queries with `ON CONFLICT DO NOTHING` for exactly-once execution.
- `StatsController` exposes `GET /api/links/{code}/stats` with proper ownership validation.

### Notes from M1-C for later tracks
- Control DB beans (`auth/ControlDbConfig`, active when `app.control-db.jdbc-url`
  set): `controlDataSource` (Hikari, 1s conn timeout), `controlFlyway`
  (programmatic, `classpath:db/migration/control`), `controlEntityManagerFactory`
  (persistence unit "control", scans auth package only),
  `controlTransactionManager`. Auth repos bind via `@EnableJpaRepositories`
  on ControlDbConfig. Shard-side JPA scoping moved to root `ShardJpaConfig`
  (@EntityScan/@EnableJpaRepositories for shortener/ ONLY — keep the app
  class annotation-light; @WebMvcTest slices use it as context root!).
- JWT config keys: `app.jwt.secret` (env JWT_SECRET, min 32 bytes — the
  WHOLE auth stack is `@ConditionalOnProperty(app.jwt.secret)` so contexts
  without the secret skip auth cleanly), access-ttl PT15M, refresh-ttl P7D,
  issuer url-shortener. Beans: JwtService, DenylistService,
  JwtAuthenticationFilter, SecurityConfig, RestAuthEntryPoint/DeniedHandler.
- Refresh sessions: Redis `auth:refresh:{sid}` (TTL=refresh remaining),
  rotation deletes old sid. Denylist `auth:denylist:{jti}` fail-OPEN read
  (metric `auth.denylist.checks{outcome}`), writes propagate 503.
- Filter ordering solution: RateLimitFilter + JwtAuthenticationFilter
  servlet auto-registration DISABLED via FilterRegistrationConfig; both are
  wired INSIDE the security chain (SecurityConfig: rateLimit before jwt,
  jwt before UsernamePasswordAuthenticationFilter). ObjectProvider guards
  the ratelimit.enabled=false case.
- Controllers get the caller via `AuthenticatedUser` argument (request attr
  `auth.currentUser` set by the JWT filter, resolved by
  AuthenticatedUserResolver — registered in WebMvcConfig, which must stay
  dependency-free: @WebMvcTest loads every WebMvcConfigurer).
- Ownership flow: `LinkIndexRepository.findByShortCodeAndUserId` → miss =
  404 (leak-resistant). PUT mutates long_url/expires_at via Link setters
  (added to shortener/Link.java — integration-approved cross-territory
  change) under `shardRouter.executeWrite`. PUT → LinkEvent.UPDATED,
  DELETE → LinkEvent.DELETED (Track D consumes; also evict cache there).
- `user_links` (control DB V1): short_code PK, user_id, created_at + index
  (user_id, created_at DESC). Track D INSERTs on CREATED — use
  `new UserLink(shortCode, userId, createdAt)` + LinkIndexRepository.save.
- TEST HYGIENE (learned the hard way): servlet `Filter` @Components and
  `WebMvcConfigurer`s ARE auto-included by @WebMvcTest slices. Test-default
  application.yml therefore has `ratelimit.enabled: false` and NO
  `app.jwt.secret`; tests that need those set properties explicitly.
- Public vs protected: public = GET /{code} (regex), POST /api/auth/
  register|login|refresh, GET /api/links/{code}, actuator health/info/
  prometheus, swagger. Everything else under /api/** authenticated
  (including /api/links/{code}/stats — Track D note: it's under /api/**,
  so already protected; add the ownership check in the controller).

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
