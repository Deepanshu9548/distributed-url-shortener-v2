# Handoff

## Phase 1: M1-C (Auth) Wrap-up
- **Files Modified/Created**:
  - `PROGRESS.md`
  - `.env.example`
  - `src/main/resources/application.yml`
  - `src/main/java/io/portfolio/urlshortener/UrlShortenerApplication.java`
  - `src/main/java/io/portfolio/urlshortener/common/GlobalExceptionHandler.java`
  - `src/main/java/io/portfolio/urlshortener/shortener/Link.java`
  - `src/main/java/io/portfolio/urlshortener/ShardJpaConfig.java`
  - `src/main/java/io/portfolio/urlshortener/auth/*`
  - `src/main/resources/db/migration/control/*`
  - `src/test/java/io/portfolio/urlshortener/auth/*`
  - `src/test/resources/application.yml`
- **Test counts before/after**: Verified ~40 tests passed for auth.
- **Deviations**: None.
- **Integration bugs found**: None.
- **Notes for next agent**: M1-C was completed by previous session. Commits split into JDK toolchain bumps and M1-C auth implementation.

## Phase 2: M1-D (Analytics)
- **Files Modified/Created**:
  - `src/main/resources/db/migration/control/V2__create_analytics.sql`
  - `src/main/java/io/portfolio/urlshortener/analytics/*`
  - `src/main/java/io/portfolio/urlshortener/events/*`
  - `src/test/java/io/portfolio/urlshortener/analytics/*`
  - `src/test/java/io/portfolio/urlshortener/events/*`
- **Test counts before/after**: Added `KafkaEventPublisherTest`, `ConsumersTest`, and `StatsControllerTest`.
- **Deviations**: None. Followed ADR-007 for exactly-once delivery.
- **Integration bugs found**: None during this phase.
- **Notes for next agent**: Analytics is fully integrated and tested in isolation.

## Phase 3: M2 (Integration)
- **Files Modified/Created**:
  - `src/main/java/io/portfolio/urlshortener/ShardJpaConfig.java`
  - `src/main/java/io/portfolio/urlshortener/auth/ControlDbConfig.java`
  - `src/main/java/io/portfolio/urlshortener/auth/JwtService.java`
  - `src/main/java/io/portfolio/urlshortener/auth/RefreshTokenService.java`
  - `src/main/java/io/portfolio/urlshortener/auth/UserService.java`
  - `src/main/java/io/portfolio/urlshortener/ratelimit/RedisTokenBucketRateLimiter.java`
  - `src/main/java/io/portfolio/urlshortener/shortener/IdempotencyKey.java`
  - `src/main/java/io/portfolio/urlshortener/shortener/SnowflakeIdGenerator.java`
  - `src/main/resources/db/migration/shard/V1__create_links.sql`
  - `src/test/java/io/portfolio/urlshortener/integration/EndToEndIT.java`
  - `src/test/java/io/portfolio/urlshortener/integration/FilterChainOrderTest.java`
  - `src/test/java/io/portfolio/urlshortener/integration/IntegrationSanityTest.java`
- **Test counts before/after**: Added `EndToEndIT`, `FilterChainOrderTest`, and `IntegrationSanityTest`. Total test pass rate is 100%.
- **Deviations**: Added `@Primary` annotation explicitly in `ShardJpaConfig` to help Spring Boot auto-configuration resolve the primary `EntityManagerFactory` and `TransactionManager` against the `RoutingDataSource` when multiple EMFs exist in the context.
- **Integration bugs found**:
  1. `NoUniqueBeanDefinitionException` during context initialization because `ControlDbConfig`'s EMF caused ambiguity. Fixed by using `@Primary` in `ShardJpaConfig`.
  2. Test property `app.base-url` was missing in some IT configurations, leading to `IllegalArgumentException`. Fixed by declaring it in test properties.
  3. Re-configured `IdempotencyKey` table with `NON_KEYWORDS=KEY` in test database JDBC URLs to avoid H2 reserved keyword conflicts.
- **Notes for next agent**: The integration works cleanly. End-to-end tests are fully functioning. All features are verified together. The project is ready for M3-F.
