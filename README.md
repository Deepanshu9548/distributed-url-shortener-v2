# Distributed URL Shortener v2

Portfolio-grade distributed URL shortener demonstrating sharding with
consistent hashing, cache-aside reads, async Kafka analytics, distributed rate
limiting, and resilience engineering. Java 17 · Spring Boot 3 · PostgreSQL ·
Redis · Kafka.

**Status: under construction — see [PROGRESS.md](PROGRESS.md) for the current checkpoint.**

## Quick start

```bash
cp .env.example .env   # set JWT_SECRET
docker compose -f docker/docker-compose.yml up --build
```

App via nginx: http://localhost · Prometheus: http://localhost:9090 · Grafana: http://localhost:3000

## Docs
- [PROGRESS.md](PROGRESS.md) — build checkpoints / resume protocol
- [docs/adr/](docs/adr/) — every architecture decision, with reasoning
- [docs/OWNERSHIP.md](docs/OWNERSHIP.md) — multi-agent file ownership matrix
- [docs/CONVENTIONS.md](docs/CONVENTIONS.md) — code and test conventions
