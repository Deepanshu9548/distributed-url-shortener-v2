package io.portfolio.urlshortener.auth;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.portfolio.urlshortener.shortener.InfraUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RefreshTokenService + DenylistService — Redis-backed, mocked template. */
class RedisSessionServicesTest {

    private StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Nested
    class RefreshSessions {

        @Test
        void register_writesSessionWithTtl() {
            RefreshTokenService svc = new RefreshTokenService(redis);
            svc.register("sid-1", 42L, Duration.ofDays(7));
            verify(values).set("auth:refresh:sid-1", "42", Duration.ofDays(7));
        }

        @Test
        void register_redisDown_usesFallback() {
            org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                    .when(values).set(anyString(), anyString(), any(Duration.class));

            RefreshTokenService svc = new RefreshTokenService(redis);
            assertThatCode(() -> svc.register("s", 1L, Duration.ofDays(7))).doesNotThrowAnyException();
            assertThat(svc.userIdFor("s")).isEqualTo(1L);
        }

        @Test
        void userIdFor_returnsIdOrNull() {
            when(values.get("auth:refresh:sid-1")).thenReturn("42");
            when(values.get("auth:refresh:ghost")).thenReturn(null);

            RefreshTokenService svc = new RefreshTokenService(redis);
            assertThat(svc.userIdFor("sid-1")).isEqualTo(42L);
            assertThat(svc.userIdFor("ghost")).isNull();
        }

        @Test
        void userIdFor_redisDown_failsOpenToNull() {
            when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
            assertThat(new RefreshTokenService(redis).userIdFor("sid-1")).isNull();
        }

        @Test
        void revoke_redisDown_swallowsFailureAndRemovesFallback() {
            RefreshTokenService svc = new RefreshTokenService(redis);
            svc.register("sid-1", 1L, Duration.ofDays(7));
            when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));
            
            assertThatCode(() -> svc.revoke("sid-1")).doesNotThrowAnyException();
            assertThat(svc.userIdFor("sid-1")).isNull();
        }
    }

    @Nested
    class Denylist {

        private SimpleMeterRegistry meters;
        private DenylistService svc;

        @BeforeEach
        void setUp() {
            meters = new SimpleMeterRegistry();
            svc = new DenylistService(redis, meters);
        }

        @Test
        void deny_setsKeyWithTtl_andSwallowsFailure() {
            svc.deny("jti-1", Duration.ofMinutes(5));
            verify(values).set(eq("auth:denylist:jti-1"), eq("1"), eq(Duration.ofMinutes(5)));

            org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                    .when(values).set(anyString(), anyString(), any(Duration.class));
            assertThatCode(() -> svc.deny("jti-2", Duration.ofMinutes(5))).doesNotThrowAnyException();
        }

        @Test
        void isDenied_hitAndMiss_countMetrics() {
            when(redis.hasKey("auth:denylist:bad")).thenReturn(true);
            when(redis.hasKey("auth:denylist:good")).thenReturn(false);

            assertThat(svc.isDenied("bad")).isTrue();
            assertThat(svc.isDenied("good")).isFalse();
            assertThat(counter("hit")).isEqualTo(1.0);
            assertThat(counter("miss")).isEqualTo(1.0);
        }

        @Test
        void isDenied_redisDown_failsOpenAndCounts() {
            when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));

            assertThat(svc.isDenied("any")).isFalse();
            assertThat(counter("store_unavailable")).isEqualTo(1.0);
        }

        private double counter(String outcome) {
            return meters.counter("auth.denylist.checks", "outcome", outcome).count();
        }
    }
}
