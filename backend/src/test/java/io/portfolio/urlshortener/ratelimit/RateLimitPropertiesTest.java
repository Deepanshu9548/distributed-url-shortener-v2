package io.portfolio.urlshortener.ratelimit;

import io.portfolio.urlshortener.ratelimit.RateLimitProperties.LimiterConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPropertiesTest {

    @Test
    void refillPerMillisecond_convertsFromPerMinute() {
        LimiterConfig write = new LimiterConfig(10, 60, false); // 1/s = 1/1000ms
        assertThat(write.refillPerMillisecond()).isEqualTo(60.0 / 60_000.0);

        LimiterConfig redirect = new LimiterConfig(100, 120_000, true);
        assertThat(redirect.refillPerMillisecond()).isEqualTo(2.0);
    }

    @Test
    void limiterConfig_rejectsNonPositiveCapacityAndRefill() {
        assertThatThrownBy(() -> new LimiterConfig(0, 60, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimiterConfig(-1, 60, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimiterConfig(10, 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimiterConfig(10, -5, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forName_returnsConfigForKnownName() {
        LimiterConfig write = new LimiterConfig(10, 10, false);
        RateLimitProperties props = new RateLimitProperties(true, Map.of("write", write));
        assertThat(props.forName("write")).contains(write);
    }

    @Test
    void forName_isEmptyForUnknownName() {
        RateLimitProperties props = new RateLimitProperties(true, Map.of());
        assertThat(props.forName("nope")).isEmpty();
    }

    @Test
    void nullLimiterMap_becomesEmpty() {
        RateLimitProperties props = new RateLimitProperties(true, null);
        assertThat(props.limiters()).isEmpty();
    }

    @Test
    void limitersMapIsImmutable() {
        RateLimitProperties props = new RateLimitProperties(true, Map.of("x", new LimiterConfig(1, 1, false)));
        assertThatThrownBy(() -> props.limiters().put("y", new LimiterConfig(1, 1, false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
