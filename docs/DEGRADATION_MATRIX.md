# Degradation Matrix

This document maps out the system's behavior when dependencies experience availability issues (e.g., Connection Refused, Timeout, or generic RuntimeException). The system uses Resilience4j circuit breakers to fail-fast and protect itself during sustained outages.

| Component | Failure Mode | Fallback Behavior | Metric Emitted |
| --- | --- | --- | --- |
| **Redis Cache** | Read Failure | Degrades to `Miss`, falling through to DB. | `cache.errors{source=redis}`, `resilience.fallback.total{breaker=redis-cache, outcome=fallback}` |
| **Redis Cache** | Write Failure | Degrades to no-op. | `cache.errors{source=redis}`, `resilience.fallback.total{breaker=redis-cache, outcome=fallback}` |
| **Redis Cache** | Lock Failure | Grants lock immediately (no stampede gate). | `cache.errors{source=redis}`, `resilience.fallback.total{breaker=redis-cache, outcome=fallback}` |
| **Redis RateLimiter** | Write/Auth Limiter Failure | Fails CLOSED (HTTP 503 upstream). | `ratelimit.decisions{outcome=fail_closed}`, `resilience.fallback.total{breaker=redis-ratelimit, outcome=fallback}` |
| **Redis RateLimiter** | Redirect Limiter Failure | Fails OPEN (Allows traffic). | `ratelimit.decisions{outcome=fail_open}`, `resilience.fallback.total{breaker=redis-ratelimit, outcome=fallback}` |
| **Kafka Publisher** | Send Failure / Full Buffer | Event is dropped (fire-and-forget). Exception swallowed. | `events.publish.total{outcome=error}`, `resilience.fallback.total{breaker=kafka-publisher, outcome=fallback}` |
| **Shard Replica** | Connection Refused / Timeout | Retries exactly once against the Shard Primary. | `shard.route.total{outcome=replica_fallback}` |
| **Shard Primary** | Connection Refused / Timeout (or Open Breaker) | Fails fast, propagates `ShardUnavailableException` (HTTP 503). | `resilience.fallback.total{breaker=shard-<name>, outcome=fallback}` |

*Note: Circuit breakers are configured in `application.yml` and apply fail-fast protection to prevent cascading failures.*