# Conventions

- Java 17, Spring Boot 3.3.x, package root `io.portfolio.urlshortener`.
- Package-by-feature. No `util` dumping ground.
- Constructor injection only (Lombok `@RequiredArgsConstructor` allowed). No field `@Autowired`.
- Records for DTOs/events; entities are plain JPA classes.
- Every external call has a timeout. No unbounded waits anywhere.
- Exceptions: domain exceptions extend RuntimeException, mapped centrally in `GlobalExceptionHandler`. Error body is always `{"error": "..."}`.
- Metrics: lowercase dot-separated; allowed labels: instance, shard, limiter, breaker, outcome, source. NEVER short-code or user-id labels (cardinality).
- Tests: JUnit 5 + AssertJ. Tags: `contract` (interface certification), `docker` (needs Testcontainers), `chaos`, `load`. Untagged = pure unit.
- Test naming: `XxxTest` unit, `XxxIT` integration (still run by surefire, gated by tags).
- Commits: `m<milestone>(<track>): <what>` e.g. `m1(a-core): snowflake generator`.
- Checkpoint commits tagged `checkpoint/<milestone>`.
- ADR in the same PR as the decision it records.
