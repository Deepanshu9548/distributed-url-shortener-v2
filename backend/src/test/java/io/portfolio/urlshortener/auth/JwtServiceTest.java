package io.portfolio.urlshortener.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-of-at-least-32-bytes-length!";
    private static final Instant NOW = Instant.parse("2026-07-04T10:00:00Z");

    private final User user = new User(42L, "Alice@Example.com", "alice@example.com", "hash", NOW);

    private JwtService serviceAt(Instant instant) {
        JwtProperties props = new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "test-issuer");
        return new JwtService(props, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void accessToken_roundTrips() {
        JwtService jwt = serviceAt(NOW);
        TokenPair pair = jwt.issue(user, "sid-1");

        ParsedToken parsed = jwt.parseAccess(pair.accessToken());
        assertThat(parsed.type()).isEqualTo(ParsedToken.Type.ACCESS);
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.email()).isEqualTo("Alice@Example.com");
        assertThat(parsed.jti()).isEqualTo(pair.accessJti());
        assertThat(parsed.sessionId()).isNull();
        assertThat(parsed.expiresAt()).isCloseTo(NOW.plus(Duration.ofMinutes(15)),
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void refreshToken_roundTripsWithSessionId() {
        JwtService jwt = serviceAt(NOW);
        TokenPair pair = jwt.issue(user, "sid-99");

        ParsedToken parsed = jwt.parseRefresh(pair.refreshToken());
        assertThat(parsed.type()).isEqualTo(ParsedToken.Type.REFRESH);
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.sessionId()).isEqualTo("sid-99");
    }

    @Test
    void typeMismatch_isRejectedBothWays() {
        JwtService jwt = serviceAt(NOW);
        TokenPair pair = jwt.issue(user, "sid-1");

        assertThatThrownBy(() -> jwt.parseAccess(pair.refreshToken()))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwt.parseRefresh(pair.accessToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void expiredAccessToken_isRejected() {
        TokenPair pair = serviceAt(NOW).issue(user, "sid-1");
        JwtService later = serviceAt(NOW.plus(Duration.ofMinutes(16)));

        assertThatThrownBy(() -> later.parseAccess(pair.accessToken()))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void wrongSecret_isRejected() {
        TokenPair pair = serviceAt(NOW).issue(user, "sid-1");
        JwtProperties other = new JwtProperties(
                "a-completely-different-secret-32-bytes-min!!", Duration.ofMinutes(15), Duration.ofDays(7), "test-issuer");
        JwtService wrongKey = new JwtService(other, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> wrongKey.parseAccess(pair.accessToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void garbage_isRejectedNotThrownAsJjwtException() {
        JwtService jwt = serviceAt(NOW);
        assertThatThrownBy(() -> jwt.parseAccess("not.a.jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void jtisAreUniquePerIssue() {
        JwtService jwt = serviceAt(NOW);
        assertThat(jwt.issue(user, "s1").accessJti())
                .isNotEqualTo(jwt.issue(user, "s2").accessJti());
    }

    @Test
    void shortSecret_isRejectedAtConstruction() {
        assertThatThrownBy(() -> new JwtProperties("short", Duration.ofMinutes(15), Duration.ofDays(7), "i"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
