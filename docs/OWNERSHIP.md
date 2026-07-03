# File Ownership Matrix

An agent may CREATE files in its territory and may NEVER edit another track's
files. Shared files change only via `shared-file`-labeled PRs (integration
agent approval).

| Territory | Owner | Contents |
|---|---|---|
| `contracts/` (+ `contracts/stubs/`) | M0/integration agent | Interfaces, event records, stubs. FROZEN post-M0; changes need the contract-change protocol |
| `shortener/`, `cache/` | Track A (core) | Snowflake, Base62, validation, shorten/redirect service+controller, Redis cache impl |
| `sharding/` | Track B (data) | Hash ring, routing datasource, shard config, per-shard Flyway |
| `auth/` | Track C (auth) | Users, JWT, security config, control-DB config, ownership CRUD, LinkIndex |
| `analytics/`, `events/` | Track D (analytics) | Kafka publisher impl, both consumers, stats endpoint, topics config |
| `ratelimit/`, `resources/lua/` | Track E (rate limit) | Lua script, limiter impl, filter, properties |
| `common/` | M0 + M3-G | Filters (instance, request-id), exception handler, logging config |
| `docker/`, `Dockerfile` | M0; per-track compose additions via shared-file PR | Infra |
| `application*.yml`, `pom.xml` | Shared | shared-file PR only |
| `docs/adr/` | Everyone (append-only) | One ADR per decision, numbered |
| `load-tests/`, `demo/` | M4 agent | JMeter, scripts, results |

## Contract-change protocol
1. PR labeled `contract-change` with updated interface + updated contract test.
2. Every consuming track signs off (in a solo-agent run: re-check each consumer compiles and its tests pass).
3. Merge only at a milestone boundary.
