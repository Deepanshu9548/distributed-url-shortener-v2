# EXECUTION BRIEF — Distributed URL Shortener v2 (handoff copy)

You are an autonomous coding agent taking over this repository. Work through
the phases below IN ORDER. This file is the master entry point; the repo
itself carries the rest of the context. Do not ask questions — every decision
you might be tempted to re-open has already been made and frozen.

---

## 0. Orientation (do this before writing any code)

Read, in this order:
1. `PROGRESS.md` — milestone table, the "⏸ RESUME HERE" section, the "NEXT"
   section (a full spec of the next milestone), and ALL "Notes from M1-x"
   blocks. This file is the single source of truth for project state.
2. `docs/CONVENTIONS.md` — code style, test tags, commit format.
3. `docs/OWNERSHIP.md` — which package belongs to which track.
4. `docs/adr/001` … `011` — every architecture decision, frozen. NEVER
   re-derive or "improve" these. If your instinct conflicts with an ADR, the
   ADR wins.
5. Skim `src/main/java/io/portfolio/urlshortener/contracts/` — the frozen
   interfaces everything is built against — and one implemented track (e.g.
   `ratelimit/`) to absorb the house style.

Project shape: Java 17 bytecode (build toolchain may be JDK 25 — that's fine),
Spring Boot 3.3.x, package-by-feature under `io.portfolio.urlshortener`,
PostgreSQL sharded via consistent hashing + control DB, Redis cache +
rate limiting, Kafka analytics, JWT auth. Portfolio project for distributed-
systems interviews — code quality and interview-defensible reasoning matter
as much as functionality.

### Environment probe
Run `docker info` once:
- **Docker available** → you may also run `mvn verify -Pdocker-tests`
  (Testcontainers) and the compose stack (`docker compose -f
  docker/docker-compose.yml up`) to validate end-to-end.
- **No Docker** → default `mvn verify` (unit + contract tests) is the
  gate; docker-tagged tests stay CI-validated. Do NOT weaken or delete them.

### Hard rules (apply to every phase)
- `mvn -q verify` must be GREEN before every commit. Never commit red.
- One milestone = one commit, message format `m<N>(<track>): <what>`, then
  tag `checkpoint/<milestone>`. Update `PROGRESS.md` IN the same commit
  (flip the milestone row, rewrite NEXT, append a "Notes from <milestone>"
  block for whoever comes after you).
- NEVER: rewrite existing git history, re-tag existing checkpoints, push to
  any remote, create a remote, or publish this repo anywhere.
  (`.claude/settings.json` sits in old history with a credential — the owner
  knows; leave it alone.)
- NEVER upgrade Spring Boot to 4.x / Framework 7 or bump major versions of
  dependencies. A Spring 4 upgrade is queued as its OWN milestone after M2,
  owned by someone else. Same for Lombok: this codebase deliberately uses
  NO Lombok (JDK-25 annotation-processing issues) — plain constructors.
- `contracts/` package (and its tests) is FROZEN. You implement interfaces;
  you never edit them.
- Real implementations supersede the M0 stubs via `@Primary` +
  `@ConditionalOnProperty` (see `HashRingShardRouter`, `RedisUrlCache`,
  `RedisTokenBucketRateLimiter` for the exact pattern). Stubs stay untouched.
- Metrics: lowercase dot-separated, ONLY labels {instance, shard, limiter,
  breaker, outcome, source}. Never a short-code / user-id label.
- Test hygiene (hard-won): `@WebMvcTest` slices auto-include every servlet
  `Filter` @Component and every `WebMvcConfigurer`. Keep those classes
  dependency-light or conditionally gated; test-default
  `src/test/resources/application.yml` keeps `ratelimit.enabled: false` and
  NO `app.jwt.secret` for exactly this reason. Auth-dependent tests set
  properties explicitly.

---

## PHASE 1 — Wrap up M1-C (auth) — small, do it first

State: M1-C is CODE-COMPLETE in the working tree but unverified/uncommitted
(previous session ended in a tooling outage). Follow the "⏸ RESUME HERE"
steps in PROGRESS.md, summarized:

1. `mvn -q verify`. Expect green. If a test fails, the failure is almost
   certainly a small wiring issue in the newest files (auth/ package or
   test-resources yml) — fix forward, do not delete tests or revert tracks.
2. First commit (separate!): the JDK-25 toolchain bumps that are already in
   the tree but are NOT part of auth:
   `git add pom.xml Dockerfile .github/workflows/ci.yml`
   `git commit -m "chore: build/CI toolchain to JDK 25 (bytecode stays release 17)"`
3. Milestone commit: everything else (`auth/` main+test, control migration,
   `ShardJpaConfig`, Link setters, GlobalExceptionHandler additions,
   application.yml/.env.example, PROGRESS.md with the M1-C row flipped to
   ✅ DONE + RESUME-HERE section deleted):
   `git commit -m "m1(c-auth): control db, jwt, ownership crud"` then
   `git tag checkpoint/m1-c`.

Acceptance: verify green; two clean commits; tag exists; PROGRESS.md truthful.

## PHASE 2 — M1-D analytics (Kafka)

Full frozen spec is in PROGRESS.md "NEXT" — implement exactly that. Summary:
territory `analytics/` (+ `events/` if you want the consumers separate):
- `KafkaEventPublisher` implements `contracts.EventPublisher`, @Primary +
  @ConditionalOnProperty(`app.kafka.enabled`, default false). acks=all,
  `max.block.ms=0`, partition key = shortCode, fire-and-forget with failure
  counter `events.publish.total{outcome=ok|error}`. Topics `click-events`,
  `link-events` via KafkaAdmin NewTopic beans.
- Click consumer: eventId raw-insert idempotency gate (`raw_click_events`,
  PK event_id) → increment `link_stats` (control DB; you own
  `db/migration/control/V2__create_analytics.sql`; ADR-007).
- Link-events consumer: CREATED → insert `user_links` row via M1-C's
  `LinkIndexRepository`; DELETED → delete row; UPDATED/DELETED → also
  `UrlCache.evict(shortCode)`. Same eventId dedup gate (`raw_link_events`).
- `GET /api/links/{code}/stats` — authenticated + owner-only (reuse the
  M1-C ownership-check pattern; non-owner → 404).
- Tests Docker-free by default (mock KafkaTemplate / repos); EmbeddedKafka
  (spring-kafka-test, already in pom) allowed for one happy-path IT.

Acceptance: verify green; commit `m1(d-analytics): …`; tag
`checkpoint/m1-d`; PROGRESS.md updated with a "Notes from M1-D" block and
NEXT rewritten for M2.

## PHASE 3 — M2 integration

Goal: prove all real implementations work TOGETHER (until now each track was
tested in isolation against stubs).
- Bean-resolution sanity: a full-context `@SpringBootTest` (H2 control DB,
  sharding off, ratelimit on with mocked-or-embedded Redis, kafka off) that
  boots and asserts the @Primary winners: RedisUrlCache, RedisTokenBucket-
  RateLimiter, KafkaEventPublisher-or-Noop per flag, HashRingShardRouter per
  flag.
- Filter-chain order test: with security active, assert rate-limit runs
  before JWT (e.g. hit login with an exhausted bucket → 429 even with bad
  creds; with valid token and exhausted user bucket → 429 before handler).
- End-to-end happy path IT (docker-tagged if it needs real infra): register →
  login → create link → redirect → stats reflects the click (via consumer) →
  delete → redirect now 404 and cache evicted.
- Compose stack: if Docker is available, `docker compose up` and run a smoke
  script (curl sequence) against nginx; fix compose wiring bugs you find
  (they are expected — it has never been run end-to-end). If no Docker, be
  correct by construction and note it.
- Fix any cross-track integration bugs you find. You may touch any package
  for INTEGRATION FIXES ONLY (no redesigns); document each cross-territory
  edit in the commit body.

Acceptance: verify green (+ docker profile if available); commit
`m2(integration): …`; tag `checkpoint/m2`; PROGRESS.md NEXT rewritten for
M3-F (resilience: Resilience4j breakers on DB/Redis/Kafka calls, chaos tests,
degradation matrix doc) using the frozen decisions list.

## PHASE 4 — STOP

Do NOT start M3-F, M3-G, M4, or any dependency upgrades. They are owned by
the original agent. Leave the repo on `main`, working tree CLEAN, all tags in
place.

---

## Handoff-back requirements (mandatory)

Create `HANDOFF.md` at the repo root as you go (committed with each
milestone), containing per phase:
- every file created/modified (path list),
- test counts before/after,
- deviations from this brief or PROGRESS.md specs, with one-line reasons,
- integration bugs found in earlier tracks and how you fixed them,
- anything the next agent must know (gotchas, TODOs, skipped items).

The original agent will diff your copy against the source repo and merge
selectively — clean commits, honest HANDOFF.md, and untouched history are
what make that possible.

Improvements/enhancements: welcome ONLY when they don't violate an ADR, a
frozen contract, or the phase scope — put them in their phase's commit and
list them in HANDOFF.md under "enhancements". When in doubt, skip it and note
it as a suggestion instead.
