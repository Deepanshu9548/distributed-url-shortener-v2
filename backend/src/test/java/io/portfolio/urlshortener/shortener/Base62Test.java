package io.portfolio.urlshortener.shortener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62Test {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "9, 9",
            "10, a",
            "35, z",
            "36, A",
            "61, Z",
            "62, 10",
            "3843, ZZ",
            "3844, 100",
            "238327, ZZZ",
            "9223372036854775807, aZl8N0y58M7"  // Long.MAX_VALUE
    })
    void knownVectors(long value, String encoded) {
        assertThat(Base62.encode(value)).isEqualTo(encoded);
        assertThat(Base62.decode(encoded)).isEqualTo(value);
    }

    @Test
    void roundTripPropertyOverRandomLongs() {
        Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            long value = random.nextLong() & Long.MAX_VALUE; // non-negative
            assertThat(Base62.decode(Base62.encode(value))).isEqualTo(value);
        }
    }

    @Test
    void encodedSnowflakeSizedIdsAreShort() {
        // ~2^63 max → 11 chars; typical 2026-era snowflake → 11 or fewer
        assertThat(Base62.encode(Long.MAX_VALUE)).hasSizeLessThanOrEqualTo(11);
    }

    @Test
    void encodeRejectsNegative() {
        assertThatThrownBy(() -> Base62.encode(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsInvalidCharacters() {
        assertThatThrownBy(() -> Base62.decode("abc!")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.decode("ab c")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsNullAndEmpty() {
        assertThatThrownBy(() -> Base62.decode(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.decode("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsOverflow() {
        assertThatThrownBy(() -> Base62.decode("ZZZZZZZZZZZZZ")).isInstanceOf(ArithmeticException.class);
    }
}
