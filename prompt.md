# EXECUTION BRIEF — Distributed URL Shortener v2 (handoff copy)

You are an autonomous coding agent. Complete the phases below IN ORDER.
This file is the master entry point; the rest of the context lives in the
repo. Do not ask questions — every decision you might be tempted to re-open
has already been made and frozen.

---

## 0. Orientation (mandatory, before any code)

Read, in this order:
1. `PROGRESS.md` — milestone table, "NEXT" section (a full spec for the next
   milestone), and ALL "Notes from M1-x / M2" blocks.
2. `HANDOFF.md` — what the previous agent delivered per phase.
3. `docs/CONVENTIONS.md` — code style, test tags, commit format.
4. `docs/OWNERSHIP.md` — package ownership matrix.
5. `docs/adr/001` … `011` — every frozen architecture decision. ADRs WIN
   against your instincts. If your instinct conflicts, the ADR is right.
6. Skim ONE prior track to absorb house style (e.g. `sharding/` or
   `ratelimit/`) — plain constructors, `@Primary` + `@ConditionalOnProperty`
   supersede pattern, metrics with allowed labels only.

**Environment probe**: `docker info`. Docker available → also
`mvn -Pdocker-tests verify` and (optionally) compose smoke. No Docker →
default `mvn verify` (unit + contract) is the gate; docker-tagged tests
stay CI-validated (do NOT weaken or delete them).

**Current state (verify with `git log --oneline`):**
tags m0, m1-a, m1-b, m1-e, m1-c, m1-d, m2 exist on main. 254 tests green.
NEXT per PROGRESS.md is **M3-F**.

### Hard rules (apply to every phase)
- `mvn -q verify` must be GREEN before every commit. Never commit red.
- One milestone = one commit, message `m<N>(<track>): <what>`; tag
  `checkpoint/<milestone>`. Update PROGRESS.md IN the same commit (flip the
  row, rewrite NEXT, append a "Notes from <milestone>" block).
- Also update **HANDOFF.md** at each phase with the same shape the prior
  agent used (files, test counts BEFORE/AFTER with exact numbers, deviations
  + reasons, integration bugs found, notes for next agent).
- Do NOT rewrite history, re-tag existing checkpoints, force-push, create
  a new remote, or `git push` at all — the owner will pull your branch.
  Work on branch `agent-updates-m3m4` (create from current main).
- Do NOT upgrade Spring Boot to 4.x / Framework 7, do NOT bump majors, do
  NOT add Lombok (JDK-25 breaks its annotation processor). Spring 4 is a
  separate future milestone owned by someone else.
- `contracts/` package + tests are FROZEN. You implement interfaces.
- Real impls supersede stubs via `@Primary` + `@ConditionalOnProperty` (see
  `HashRingShardRouter`, `RedisUrlCache`, `RedisTokenBucketRateLimiter`,
  `KafkaEventPublisher` for the exact pattern). Stubs stay untouched.
- Metrics: lowercase dot-separated, ONLY labels
  `{instance, shard, limiter, breaker, outcome, source}`. Never a short-code
  or user-id label.
- Test hygiene: `@WebMvcTest` slices auto-include every servlet `Filter` and
  every `WebMvcConfigurer`. Test-default `src/test/resources/application.yml`
  keeps `ratelimit.enabled: false` and NO `app.jwt.secret` for this reason.
  Auth/rate-limit-dependent tests set properties explicitly per class.
- Style: plain constructors (no Lombok), javadoc the WHY on every
  non-obvious class (that is what makes the code interview-defensible —
  reviewer feedback on prior handoff was "sparse javadoc; add the reasoning
  a reader can't derive from the code"). Never re-add redundant `@Autowired`
  to single-constructor classes.
- Write from scratch; do NOT edit files outside your territory except where
  a phase explicitly permits it. If you MUST cross a boundary for an
  integration bug, minimize the diff and list it in HANDOFF.md as a
  "cross-territory edit — reason: <one line>".

### Handoff-back requirements (mandatory)
Every phase you complete must, in the same commit:
1. Flip the PROGRESS.md milestone row to ✅ DONE + tag.
2. Rewrite PROGRESS.md "NEXT" for the next milestone.
3. Append a "Notes from M3-x / M4" block in PROGRESS.md.
4. Append a phase section to HANDOFF.md (exact test counts, files created,
   deviations, integration bugs found, notes for next agent).
5. Commit + tag.

Improvements/enhancements: welcome ONLY when they don't violate an ADR, a
frozen contract, or the phase scope. Put them in the same commit and list
them in HANDOFF.md under "enhancements". When in doubt, skip and add to
`suggestions.md` as a proposal instead.

---

## PHASE 1 — M3-F resilience (Resilience4j breakers + chaos + degradation matrix)

### Territory
- New package: `src/main/java/io/portfolio/urlshortener/resilience/**` and its
  test package.
- New doc: `docs/DEGRADATION_MATRIX.md`.
- Cross-territory edits ALLOWED (documented in HANDOFF.md — this is a
  cross-cutting concern):
  - `cache/RedisUrlCache.java` — wrap Redis calls with a breaker (see below).
  - `ratelimit/RedisTokenBucketRateLimiter.java` — same.
  - `events/KafkaEventPublisher.java` — same for producer sends.
  - `sharding/HashRingShardRouter.java` — DB call breaker (per-shard).
  - `application.yml` — Resilience4j config section.

### Frozen scope
The Resilience4j starter (`resilience4j-spring-boot3`) is already in
`pom.xml`. You configure it, apply it, and prove the degradation paths.

1. **Per-dependency circuit breakers** — declare in
   `application.yml` under `resilience4j.circuitbreaker.instances.*`:
   - `redis-cache` (used by RedisUrlCache): sliding window 20 calls,
     failure-rate threshold 50%, wait 5s in open, half-open 3 calls.
   - `redis-ratelimit` (used by RedisTokenBucketRateLimiter): same params.
   - `kafka-publisher` (used by KafkaEventPublisher): failure threshold 50%,
     wait 10s.
   - `shard-<name>` per shard (used by HashRingShardRouter — one breaker per
     shard so one dead shard can't trip the others).
2. **Wrap the actual calls** with `CircuitBreaker.decorateSupplier(...)`
   OR the `@CircuitBreaker(name = "...", fallbackMethod = "...")` annotation
   (AOP, spring-aop is already on classpath). The fallback for each MUST
   preserve the existing degradation contract:
   - `redis-cache` fallback → `UrlCache.Miss` on reads, no-op on writes
     (identical to today's `swallow(...)` semantics — the breaker just adds
     "and stop trying for 5s").
   - `redis-ratelimit` fallback → per-limiter `failOpen` / `failClosed`
     policy (identical to today).
   - `kafka-publisher` fallback → increment
     `events.publish.total{outcome=error}` and drop the event (fire-and-
     forget is already the contract; the breaker just avoids the send call
     when Kafka is known-bad).
   - `shard-<name>` fallback → propagate the exception (breaker just fails
     fast; RouterShardRouter's replica→primary fallback still runs FIRST on
     the replica breaker, then trips the primary breaker if that ALSO fails).
3. **Metrics**: rely on the resilience4j-micrometer integration (already in
   pom) — auto-exposes
   `resilience4j.circuitbreaker.state`,
   `resilience4j.circuitbreaker.calls{outcome}`. Add
   `resilience.fallback.total{breaker=<name>, outcome=fallback|passthrough}`
   ONE counter for observability of "was this response from a fallback path".
4. **Chaos tests** (`@Tag("chaos")` — the surefire profile already
   understands this tag, DO NOT change pom):
   - `RedisCacheChaosTest`: put a working RedisUrlCache in front of a
     Redis mock that throws for N calls, verify the breaker opens after
     threshold, that reads become `Miss` during the open window, that the
     redirect path still works via DB, and that the breaker closes again
     after the half-open probe succeeds.
   - `KafkaPublisherChaosTest`: send N times against a mock that fails,
     assert breaker opens, `events.publish.total{outcome=error}` counted,
     no exception propagates to caller (contract preserved).
   - `ShardRouterChaosTest`: shard1 breaker opens → executeWrite/Read to a
     shard1 key propagates a clear "shard unavailable" domain error
     (introduce `ShardUnavailableException` in sharding/ package;
     GlobalExceptionHandler maps to 503 — you own that edit); shard2 keys
     continue to work (per-shard isolation).
5. **Degradation matrix doc** at `docs/DEGRADATION_MATRIX.md` — one table
   with rows = failure modes (Redis down, single shard down, both shards
   down, Kafka down, control-DB down), columns = each user-facing endpoint
   (POST /api/links, GET /{code}, POST /api/auth/login, GET /api/me/links,
   GET /api/links/{code}/stats), cells = the observed behavior after M3-F
   (with a short reason). This is the interview artifact.

### Testing
- All non-chaos tests remain green (default `mvn verify`).
- `mvn -q -Pchaos-tests verify` runs the chaos suite (the `chaos-tests`
  profile already exists in pom.xml). Must be green.
- No Docker required for chaos tests — the failing dependency is mocked.

### Acceptance
- `mvn -q verify` green, `mvn -q -Pchaos-tests verify` green.
- `docs/DEGRADATION_MATRIX.md` filled in and cross-referenced from
  PROGRESS.md.
- Commit `m3(f-resilience): ...` + tag `checkpoint/m3-f`.
- PROGRESS.md M3-F row flipped, NEXT rewritten for M3-G, "Notes from M3-F"
  appended (breaker names, fallback semantics, metric names, chaos-suite
  invocation).
- HANDOFF.md phase section added with real numbers.

---

## PHASE 2 — M3-G observability (metrics, dashboards, structured logs)

### Territory
- New package: `src/main/java/io/portfolio/urlshortener/observability/**`.
- `src/main/java/io/portfolio/urlshortener/common/RequestIdFilter.java` (new;
  co-owner with M0 per OWNERSHIP.md).
- `src/main/resources/logback-spring.xml` (new — structured logging).
- `docker/grafana/provisioning/dashboards/**` (new dashboards).
- `docker/prometheus/prometheus.yml` (extend scrape config if needed).

### Frozen scope
1. **Structured logs** via logstash-logback-encoder (already in pom):
   `logback-spring.xml` with a JSON encoder to stdout, MDC keys
   `requestId`, `instance`, `userId`, `shortCode` (userId/shortCode only when
   available and safe — never in metrics labels, they're OK in logs).
2. **`RequestIdFilter`** — OncePerRequestFilter,
   `@Order(Ordered.HIGHEST_PRECEDENCE + 1)` (right after
   InstanceHeaderFilter). Reads `X-Request-Id` header, generates a UUID
   if absent, puts it in MDC + on response header. Auth filter, rate-limit
   filter, and services should already reference `requestId` in their
   ClickEvent / LinkEvent construction (verify — the field exists in the
   contracts).
3. **Actuator + Prometheus** are already exposed (`management.endpoints...`
   configured). Add per-endpoint SLO histograms only for endpoints that
   don't already have them (`http.server.requests` is global; add explicit
   histograms for the redirect and shorten paths only if the existing
   config doesn't already publish them — check first, don't duplicate).
4. **Grafana dashboards** in
   `docker/grafana/provisioning/dashboards/` — one JSON dashboard per
   subsystem:
   - `url-shortener-overview.json`: RPS, p50/p95/p99 latency by endpoint,
     error rate, instance count.
   - `cache-and-ratelimit.json`: cache hit/miss/negative/error,
     ratelimit.decisions by outcome, denylist checks by outcome.
   - `sharding-and-analytics.json`: shard.route.total by shard/outcome,
     events.publish.total, resilience4j breaker states.
   Dashboards must reference existing metric names — do not invent new
   metrics for the dashboards; if a panel needs data that isn't emitted,
   emit the metric (with allowed labels only) and note it in HANDOFF.md.
5. **README section** — add a "Observability" section to `README.md` with
   the URLs (Grafana http://localhost:3000, Prometheus :9090) and one
   screenshot placeholder per dashboard (do NOT embed images; leave
   `<!-- screenshot: overview.png -->` markers so the M4 agent can capture
   them during the demo run).

### Testing
- `RequestIdFilterTest` — X-Request-Id passthrough, generation on absence,
  MDC set + cleared, header on response.
- `LoggingConfigTest` — smoke test that a JSON encoder is on the root
  appender in the test config (or a targeted test with a capturing
  appender that asserts JSON shape).
- No dashboard test — provisioning JSON is validated by whether Grafana
  loads it in compose (docker-tagged smoke test optional).

### Acceptance
- `mvn -q verify` green.
- `docker/grafana/provisioning/dashboards/*.json` load cleanly (if Docker
  available, spin up compose and eyeball each dashboard; document what you
  saw in HANDOFF.md).
- Commit `m3(g-observability): ...` + tag `checkpoint/m3-g`.
- PROGRESS.md updated, "Notes from M3-G" appended, NEXT rewritten for M4.
- HANDOFF.md phase section added.

---

## PHASE 3 — M4 load + packaging + defense notes

### Territory
- `load-tests/**` (new; per OWNERSHIP.md).
- `demo/**` (new; per OWNERSHIP.md).
- `docs/DEFENSE_NOTES.md` (new; the interview-facing artifact).
- `README.md` — replace the "under construction" line and expand to a full
  quickstart + architecture blurb.
- No src/main changes unless a load test surfaces a real bug.

### Frozen scope
1. **JMeter test plan** at `load-tests/redirect.jmx` — 10K sustained req/s
   for 5 minutes against `GET /{code}` after seeding N pre-created links.
   Also a `write.jmx` for POST /api/links at a lower rate (~500/s). Include
   a seed script `load-tests/seed.sh` that hits `/api/links` to create the
   pool of codes.
2. **Run the load test** if Docker is available (compose stack + JMeter in
   a container). Capture results to `load-tests/results/<timestamp>/`:
   - `redirect.jtl` (raw), `redirect-summary.txt` (throughput, p50/p95/p99,
     error rate), one Grafana screenshot placeholder.
   - `write-summary.txt`.
   If Docker is NOT available, note it in HANDOFF.md and skip runs; ship
   the plan.
3. **Demo script** at `demo/demo.sh` — end-to-end curl walkthrough:
   register → login → create link → redirect (assert 302) → wait 1s →
   stats (assert click_count=1) → PUT (change longUrl) → redirect again
   → DELETE → redirect 404. Runnable against compose. Include a
   `demo/README.md` with expected outputs so an interviewer sees the full
   story.
4. **Defense notes** at `docs/DEFENSE_NOTES.md` — interview-ready
   reasoning per decision. Structure:
   - Snowflake vs UUID (why 41/10/12 + epoch choice)
   - Consistent hashing (why murmur3_32_fixed + 150 vnodes; remap fraction)
   - Cache-aside + negative cache + stampede lock (why not read-through)
   - Token bucket in Lua (why atomic, why asymmetric fail policy)
   - Kafka async click pipeline (why not sync; exactly-once-effect vs.
     exactly-once-delivery)
   - JWT stateless (why 15m access + 7d refresh + Redis denylist fail-open)
   - Control DB off the ring (why the ownership index is not in a shard)
   - Circuit breakers per dependency (why per-shard isolation, why
     fallbacks preserve degradation contracts).
   - "What I'd do next" (link to `suggestions.md` — virtual threads,
     edge caching, GraalVM, etc.).
5. **README rewrite**:
   - Remove the "under construction" line.
   - Add: architecture diagram (ASCII is fine or a link to a placeholder),
     quickstart (compose up + demo.sh), directory tour, links to PROGRESS
     (history), HANDOFF (recent handoffs), ADRs (decisions), degradation
     matrix, defense notes.
   - Add a "Results" section referencing the load-test summary numbers.

### Testing
- No new test code required (M4 is packaging/demo/docs).
- All prior `mvn verify` runs stay green — verify one final time.

### Acceptance
- `mvn -q verify` green.
- `demo/demo.sh` documented + tested if Docker available (mark exit code
  and expected output in HANDOFF.md); shipped even if not runnable here.
- `docs/DEFENSE_NOTES.md` complete.
- `README.md` no longer says "under construction".
- Commit `m4(load-and-packaging): ...` + tag `checkpoint/m4`.
- PROGRESS.md: M4 row flipped, NEXT reads "PROJECT COMPLETE — next queued
  work is the Spring Boot 4 upgrade, owned separately."
- HANDOFF.md phase section added.

---

## PHASE 4 — STOP

Do NOT start the Spring Boot 4 upgrade, do NOT bump dependency majors, do
NOT touch `.claude/settings.json` (still holds a live local credential in
git history — the owner will handle it). Leave the branch clean and let
the owner review + merge.

---

## Summary of what you deliver
- Branch `agent-updates-m3m4` with three clean milestone commits + three
  tags.
- Updated `PROGRESS.md`, `HANDOFF.md`, `README.md`, and new docs
  `docs/DEGRADATION_MATRIX.md`, `docs/DEFENSE_NOTES.md`.
- New packages `resilience/`, `observability/`.
- New assets `load-tests/`, `demo/`, Grafana dashboard JSON.
- All `mvn verify` runs green; chaos suite green under `-Pchaos-tests`.
- Honest HANDOFF.md with exact test counts, files, deviations, and any
  integration bugs surfaced along the way.
