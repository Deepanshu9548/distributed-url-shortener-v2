package io.portfolio.urlshortener.integration;

import io.portfolio.urlshortener.analytics.LinkStats;
import io.portfolio.urlshortener.analytics.LinkStatsRepository;
import io.portfolio.urlshortener.auth.User;
import io.portfolio.urlshortener.auth.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("docker")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.sharding.enabled=true",
        "app.sharding.shards[0].name=shard1",
        "app.sharding.shards[0].primary.jdbc-url=jdbc:h2:mem:e2e_shard1;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=KEY",
        "app.sharding.shards[0].primary.username=sa",
        "app.sharding.shards[0].primary.password=",
        "app.sharding.shards[0].replica.jdbc-url=jdbc:h2:mem:e2e_shard1;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=KEY",
        "app.sharding.shards[0].replica.username=sa",
        "app.sharding.shards[0].replica.password=",
        "ratelimit.enabled=true",
        "app.kafka.enabled=true",
        "app.node-id=1",
        "app.jwt.secret=test-secret-min-32-bytes-long-for-hmac",
        "app.control-db.jdbc-url=jdbc:h2:mem:e2e_control;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "app.control-db.username=sa",
        "app.control-db.password=",
        "app.control-db.pool-size=2",
        "app.base-url=http://localhost:8080"
})
class EndToEndIT {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LinkStatsRepository linkStatsRepository;

    @Test
    void happyPath_createsLink_andTracksClicks() {
        // 1. Register User
        ResponseEntity<Map> regRes = restTemplate.postForEntity(
                "/api/auth/register",
                Map.of("email", "e2e@example.com", "password", "password"),
                Map.class);
        assertEquals(HttpStatus.OK, regRes.getStatusCode());

        // 2. Login
        ResponseEntity<Map> loginRes = restTemplate.postForEntity(
                "/api/auth/login",
                Map.of("email", "e2e@example.com", "password", "password"),
                Map.class);
        assertEquals(HttpStatus.OK, loginRes.getStatusCode());
        String token = (String) loginRes.getBody().get("token");

        // 3. Create Link
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Idempotency-Key", "e2e-key-1");
        
        HttpEntity<Map<String, String>> createReq = new HttpEntity<>(Map.of("url", "https://example.com"), headers);
        ResponseEntity<Map> createRes = restTemplate.postForEntity("/api/links", createReq, Map.class);
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
        String shortCode = (String) createRes.getBody().get("shortCode");
        assertNotNull(shortCode);

        // 4. Click Link
        ResponseEntity<Void> clickRes = restTemplate.getForEntity("/" + shortCode, Void.class);
        assertEquals(HttpStatus.FOUND, clickRes.getStatusCode());
        assertEquals("https://example.com", clickRes.getHeaders().getLocation().toString());

        // 5. Verify Stats (Kafka consumer updates stats async)
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            Optional<LinkStats> stats = linkStatsRepository.findById(shortCode);
            return stats.isPresent() && stats.get().getClickCount() == 1;
        });
        
        // 6. Verify Stats API
        ResponseEntity<Map> statsRes = restTemplate.exchange(
                "/api/links/" + shortCode + "/stats",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        assertEquals(HttpStatus.OK, statsRes.getStatusCode());
        assertEquals(1, ((Number) statsRes.getBody().get("clickCount")).intValue());
    }
}
