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
| M1-A core (Snowflake, Base62, shorten/redirect, cache-aside) | ⬜ next | — |
| M1-B data (hash ring, shard routing, replicas) | ⬜ | — |
| M1-C auth (JWT, users, ownership CRUD) | ⬜ | — |
| M1-D analytics (Kafka, consumers, stats) | ⬜ | — |
| M1-E rate limiting (Lua token bucket, filter) | ⬜ | — |
| M2 integration (swap stubs, filter order, e2e) | ⬜ | — |
| M3-F resilience (breakers, chaos, degradation matrix) | ⬜ | — |
| M3-G observability (metrics, dashboards, logs) | ⬜ | — |
| M4 load + packaging (JMeter, README, demo, defense notes) | ⬜ | — |

## NEXT
Execute M1 tracks A–E (parallelizable; task board has them as tasks #6,#2,#5,#3,#4).
Track order if sequential: A → B → E → C → D.
Each track: implement in its owned territory (see docs/OWNERSHIP.md), pass its
contract tests, `mvn verify` green, commit `checkpoint/m1-<track>`.

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
