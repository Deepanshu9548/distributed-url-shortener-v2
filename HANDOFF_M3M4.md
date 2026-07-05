# HANDOFF_M3M4

## Test counts (exact)
- default `mvn verify` before your work: 254
- default `mvn verify` after your work: 254
- `mvn -Pchaos-tests verify` after your work: 257
- list every new test class you added, one per line:
io.portfolio.urlshortener.resilience.RedisCacheChaosTest
io.portfolio.urlshortener.resilience.KafkaPublisherChaosTest
io.portfolio.urlshortener.resilience.ShardRouterChaosTest

## Files created (full paths)
demo/README.md
demo/demo.sh
deploy/monitoring/dashboards/url_shortener.json
docs/DEFENSE_NOTES.md
docs/DEGRADATION_MATRIX.md
load-tests/redirect.jmx
load-tests/seed.sh
load-tests/write.jmx
src/main/java/io/portfolio/urlshortener/common/RequestIdFilter.java
src/main/java/io/portfolio/urlshortener/sharding/ShardUnavailableException.java
src/main/resources/logback-spring.xml
src/test/java/io/portfolio/urlshortener/resilience/KafkaPublisherChaosTest.java
src/test/java/io/portfolio/urlshortener/resilience/RedisCacheChaosTest.java
src/test/java/io/portfolio/urlshortener/resilience/ShardRouterChaosTest.java

## Files modified (full paths + one-line reason each)
pom.xml - Added chaos-tests profile to include chaos test group.
src/main/java/io/portfolio/urlshortener/cache/RedisUrlCache.java - Wrapped cache calls in CircuitBreaker and registered metrics.
src/main/java/io/portfolio/urlshortener/common/GlobalExceptionHandler.java - Added handling for ShardUnavailableException to return 503.
src/main/java/io/portfolio/urlshortener/events/KafkaEventPublisher.java - Wrapped Kafka sends in CircuitBreaker.
src/main/java/io/portfolio/urlshortener/ratelimit/RedisTokenBucketRateLimiter.java - Wrapped Lua script execution in CircuitBreaker.
src/main/java/io/portfolio/urlshortener/sharding/HashRingShardRouter.java - Added per-shard CircuitBreaker wrap.
src/main/resources/application.yml - Configured Resilience4j instances (redis-cache, redis-ratelimit, kafka-publisher, shard-*).
src/test/java/io/portfolio/urlshortener/cache/RedisUrlCacheIT.java - Added CircuitBreakerRegistry mock to constructor.
src/test/java/io/portfolio/urlshortener/cache/RedisUrlCacheTest.java - Added CircuitBreakerRegistry mock to constructor.
src/test/java/io/portfolio/urlshortener/events/KafkaEventPublisherTest.java - Added CircuitBreakerRegistry mock to constructor.
src/test/java/io/portfolio/urlshortener/ratelimit/RedisTokenBucketRateLimiterTest.java - Added CircuitBreakerRegistry mock to constructor.
src/test/java/io/portfolio/urlshortener/sharding/HashRingShardRouterContractTest.java - Added CircuitBreakerRegistry mock to constructor.
src/test/java/io/portfolio/urlshortener/sharding/HashRingShardRouterTest.java - Added CircuitBreakerRegistry mock to constructor.
src/test/java/io/portfolio/urlshortener/sharding/RoutingDataSourceTest.java - Added CircuitBreakerRegistry mock to constructor.

## Per-phase notes
### M3-F
- circuit breaker instance names configured: redis-cache, redis-ratelimit, kafka-publisher, shard-shard1, shard-shard2
- which methods each breaker wraps (class#method):
  - RedisUrlCache#get, RedisUrlCache#put, RedisUrlCache#evict
  - RedisTokenBucketRateLimiter#tryAcquire
  - KafkaEventPublisher#publish
  - HashRingShardRouter#executeRead, HashRingShardRouter#executeWrite
- what each fallback returns:
  - redis-cache: UrlCache.Miss for get(), no-op for put()/evict()
  - redis-ratelimit: per-limiter failOpen boolean
  - kafka-publisher: drops event, increments fallback metric, returns without exception
  - shard-<name>: throws ShardUnavailableException
- chaos test invocation command + result summary: `mvn test -Pchaos-tests -Dgroups=chaos`. Results in verified open/half-open state transitions and fallback logic execution.

### M3-G
- RequestIdFilter order + header name: Ordered.HIGHEST_PRECEDENCE + 1, header name `X-Request-Id`
- MDC keys populated: `requestId`, `userId`, `shortCode`
- logback-spring.xml appender type: ConsoleAppender with LogstashEncoder (JSON layout)
- list of Grafana dashboard files with panel counts: `deploy/monitoring/dashboards/url_shortener.json` (3 panels)

### M4
- JMeter plan filenames + configured RPS/duration: `redirect.jmx` (10,000 req/s, 300s duration), `write.jmx` (500 req/s, 300s duration)
- demo/demo.sh exit code when you ran it (or "not run: no Docker"): not run: no Docker
- DEFENSE_NOTES.md section headings:
  - 1. Snowflake IDs vs UUIDs
  - 2. Consistent Hashing
  - 3. Cache-Aside + Negative Cache + Stampede Lock
  - 4. Token Bucket in Lua
  - 5. Kafka Async Click Pipeline
  - 6. Stateless JWT & Redis Denylist
  - 7. Control DB Off the Ring
  - 8. Circuit Breakers per Dependency
  - What I'd Do Next (Suggestions)

## Deviations from prompt.md
- Skipped running the JMeter load test and taking Grafana screenshots: Docker was not available on the agent host environment, shipped test plans instead.

## Cross-territory edits
- src/main/java/io/portfolio/urlshortener/cache/RedisUrlCache.java: Added Resilience4j wrapping
- src/main/java/io/portfolio/urlshortener/events/KafkaEventPublisher.java: Added Resilience4j wrapping
- src/main/java/io/portfolio/urlshortener/ratelimit/RedisTokenBucketRateLimiter.java: Added Resilience4j wrapping
- src/main/java/io/portfolio/urlshortener/sharding/HashRingShardRouter.java: Added per-shard Resilience4j wrapping
- src/main/resources/application.yml: Added Resilience4j configuration properties
- src/main/java/io/portfolio/urlshortener/common/GlobalExceptionHandler.java: Added ShardUnavailableException handler
- pom.xml: Profile for chaos-tests group filtering.

## Concerns / open questions for the owner
- Ensure you have a clean way to untrack `.claude/settings.json` and rotate the exposed credentials, as that was explicitly left for you to handle!