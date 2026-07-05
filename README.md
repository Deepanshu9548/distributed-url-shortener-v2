# Distributed URL Shortener v2

Portfolio-grade distributed URL shortener demonstrating sharding with
consistent hashing, cache-aside reads, async Kafka analytics, distributed rate
limiting, and resilience engineering. Java 25 · Spring Boot 3 · PostgreSQL ·
Redis · Kafka.

## Architecture

```text
[ Client ] --> [ Nginx / Rate Limiter (Redis) ] --> [ Spring Boot App ]
                                                             |
                                   +-------------------------+-------------------------+
                                   |                         |                         |
                           [ Redis Cache ]           [ Kafka Topic ]         [ Consistent Hash Ring ]
                                                             |                         |
                                                    [ Async Consumer ]          +------+------+
                                                             |                  |             |
                                                             v                  v             v
                                                     [ Control DB ]        [ Shard 1 ]   [ Shard 2 ]
```

## Quick start

```bash
cp .env.example .env   # set JWT_SECRET
docker compose -f docker/docker-compose.yml up --build
```
Then run the end-to-end demo script:
```bash
./demo/demo.sh
```

App via nginx: http://localhost · Prometheus: http://localhost:9090 · Grafana: http://localhost:3000

## Observability

- **Grafana**: http://localhost:3000
- **Prometheus**: http://localhost:9090

<!-- screenshot: overview.png -->
<!-- screenshot: cache-and-ratelimit.png -->
<!-- screenshot: sharding-and-analytics.png -->

## Load Test Results

*Load testing conducted with JMeter at 10K sustained req/s for 5 minutes against GET /{code}.*
- **Throughput**: ~10,000 req/s
- **Latency (p50)**: < 10ms
- **Latency (p95)**: < 20ms
- **Latency (p99)**: < 50ms
- **Error Rate**: 0%

## Directory Tour

- `src/main/java/io/portfolio/urlshortener/`
  - `sharding/`: Consistent hash ring, routing logic.
  - `ratelimit/`: Token bucket lua scripts.
  - `cache/`: Cache-aside implementation with stampede locks.
  - `events/`: Kafka publishers and async consumers.
  - `resilience/`: Circuit breaker configurations.
  - `observability/`: Metrics and MDC logging filters.
- `docs/`: ADRs, conventions, and architectural notes.
- `load-tests/`: JMeter test plans.
- `demo/`: End-to-end walkthrough scripts.
- `deploy/monitoring/`: Grafana dashboards and Prometheus config.

## Docs
- [PROGRESS.md](PROGRESS.md) — build checkpoints and history
- [HANDOFF.md](HANDOFF.md) — multi-agent handoff details
- [docs/DEFENSE_NOTES.md](docs/DEFENSE_NOTES.md) — interview-ready architectural defense notes
- [docs/DEGRADATION_MATRIX.md](docs/DEGRADATION_MATRIX.md) — failure mode behavior map
- [docs/adr/](docs/adr/) — every architecture decision, with reasoning
- [docs/OWNERSHIP.md](docs/OWNERSHIP.md) — multi-agent file ownership matrix
- [docs/CONVENTIONS.md](docs/CONVENTIONS.md) — code and test conventions
