# Architectural Suggestions & Improvements

This document outlines potential improvements for the Distributed URL Shortener v2. These are recommendations for future iterations (e.g., M5 and beyond).

## 1. Upgrades
- **Spring Boot 4 / Framework 7:** The system currently runs on Spring Boot 3.3.5 and Java 25. Moving to Spring Boot 4 will allow us to leverage deeper integration with Project Loom (Virtual Threads) by default and fully optimized GraalVM native image compilation, which significantly reduces the startup time for auto-scaling events.
- **Jakarta EE 11:** Future-proofing the Servlet and Validation APIs.

## 2. Scalability & Performance
- **Edge Caching:** The current architecture caches heavily in Redis. Introducing an Edge caching layer (e.g., CDN, Cloudflare Workers) could serve hot short codes directly from the edge, dropping origin traffic for viral links to near zero.
- **Virtual Threads (Project Loom):** For all I/O bound operations (Database calls, Redis interactions, HTTP requests), replacing the standard thread pool with virtual threads will massively increase the concurrent connection capacity of each JVM instance without increasing memory pressure.
- **Reactive Stack:** Although the current stack uses standard MVC + Tomcat, migrating to Spring WebFlux + Netty + R2DBC could provide marginal throughput gains for this highly I/O bound application, though it significantly complicates the programming model.

## 3. Data Storage & Sharding
- **Cassandra / ScyllaDB:** The current PostgreSQL sharding setup (using consistent hashing to route to multiple Postgres databases) works well for medium scale. However, replacing it with a natively distributed NoSQL store like ScyllaDB would eliminate the need for manual connection-pooling and application-side Hash Ring management, as the database cluster inherently handles partitioning and replication.
- **Read Replicas:** The router currently supports falling back to a primary if a replica fails. Setting up actual read replicas and configuring the `HashRingShardRouter` to intelligently distribute read load across replicas would improve read throughput.

## 4. Analytics & Processing
- **Apache Flink:** The current Kafka consumer writes directly to the control DB via upserts. As click volume grows, moving to a stream processing framework like Apache Flink would allow us to compute time-windowed aggregates (e.g., clicks per minute, geographic breakdowns) much more efficiently before persisting to the DB.
- **ClickHouse:** Storing raw click events or heavy aggregations in PostgreSQL will eventually cause bloat. Offloading analytics to an OLAP database like ClickHouse is recommended for long-term retention and complex querying.

## 5. Security Enhancements
- **Rate Limiting Refinements:** The current rate limiter relies on IP and User ID. Adding device fingerprinting or bot-protection headers (e.g., reCAPTCHA for the shortening endpoint) would prevent sophisticated distributed scraping or spamming.
