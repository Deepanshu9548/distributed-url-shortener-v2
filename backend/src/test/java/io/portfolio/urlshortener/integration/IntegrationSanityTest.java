package io.portfolio.urlshortener.integration;

import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.RateLimiter;
import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.contracts.UrlCache;
import io.portfolio.urlshortener.ratelimit.RedisTokenBucketRateLimiter;
import io.portfolio.urlshortener.contracts.stubs.NoopCache;
import io.portfolio.urlshortener.contracts.stubs.NoopPublisher;
import io.portfolio.urlshortener.contracts.stubs.SingleShardRouter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "app.sharding.enabled=true",
        "app.sharding.shards[0].name=shard1",
        "app.sharding.shards[0].primary.jdbc-url=jdbc:h2:mem:shard1_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=KEY",
        "app.sharding.shards[0].primary.username=sa",
        "app.sharding.shards[0].primary.password=",
        "app.sharding.shards[0].replica.jdbc-url=jdbc:h2:mem:shard1_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=KEY",
        "app.sharding.shards[0].replica.username=sa",
        "app.sharding.shards[0].replica.password=",
        "ratelimit.enabled=true",
        "app.kafka.enabled=false",
        "app.node-id=1",
        "app.jwt.secret=test-secret-min-32-bytes-long-for-hmac",
        "app.control-db.jdbc-url=jdbc:h2:mem:control_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "app.control-db.username=sa",
        "app.control-db.password=",
        "app.control-db.pool-size=2",
        "app.base-url=http://localhost:8080"
})
class IntegrationSanityTest {

    @Autowired private ShardRouter shardRouter;
    @Autowired private UrlCache urlCache;
    @Autowired private RateLimiter rateLimiter;
    @Autowired private EventPublisher eventPublisher;

    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean(name = "tokenBucketScript") private RedisScript<Long> tokenBucketScript;

    @Test
    void testPrimaryBeansResolution() {
        assertTrue(shardRouter instanceof io.portfolio.urlshortener.sharding.HashRingShardRouter, "Sharding on -> HashRingShardRouter");
        assertFalse(urlCache instanceof NoopCache, "Redis cache should be primary");
        assertTrue(rateLimiter instanceof RedisTokenBucketRateLimiter, "Redis rate limiter should be primary");
        assertTrue(eventPublisher instanceof NoopPublisher, "Kafka off -> stub");
    }
}
