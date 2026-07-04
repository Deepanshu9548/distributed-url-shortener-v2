package io.portfolio.urlshortener.shortener;

/**
 * Base62 codec over the alphabet {@code [0-9a-zA-Z]} (index order: digits,
 * lowercase, uppercase). Encodes non-negative longs — Snowflake ids are always
 * non-negative (sign bit unused, ADR-001).
 */
public final class Base62 {

    static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;

    private Base62() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Base62.encode requires a non-negative value: " + value);
        }
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder(11); // Long.MAX_VALUE is 11 base62 digits
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Base62.decode requires a non-empty string");
        }
        long value = 0;
        for (int i = 0; i < encoded.length(); i++) {
            int digit = digitOf(encoded.charAt(i));
            value = Math.addExact(Math.multiplyExact(value, BASE), digit); // overflow → ArithmeticException
        }
        return value;
    }

    private static int digitOf(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 36;
        }
        throw new IllegalArgumentException("invalid base62 character: '" + c + "'");
    }
}
